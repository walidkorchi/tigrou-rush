package io.github.rush.utils;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class OggDurationReader {

    private OggDurationReader() {}

    public static long getDurationMs(File zipFile, String entryPath) {
        if (!zipFile.exists()) return -1;
        try (ZipFile zip = new ZipFile(zipFile)) {
            ZipEntry entry = zip.getEntry(entryPath);
            if (entry == null) return -1;
            long size = entry.getSize();
            if (size <= 0) return -1;
            try (InputStream is = zip.getInputStream(entry)) {
                return parseOggDuration(is);
            }
        } catch (IOException e) {
            return -1;
        }
    }

    private static long parseOggDuration(InputStream is) throws IOException {
        DataInputStream dis = new DataInputStream(is);
        long granulePosition = 0;
        int sampleRate = 44100;

        byte[] magic = new byte[4];
        while (true) {
            try {
                dis.readFully(magic); // read(byte[]) may return fewer than 4 bytes on ZipInputStream
            } catch (java.io.EOFException e) {
                break;
            }
            if (magic[0] != 'O' || magic[1] != 'g' || magic[2] != 'g' || magic[3] != 'S') break;

            dis.readByte();
            byte headerType = dis.readByte();

            long gp = readInt64Le(dis);
            if (gp > 0) granulePosition = gp;

            readInt32Le(dis);
            int pageSeq = readInt32Le(dis);
            readInt32Le(dis);
            int numSegments = dis.readUnsignedByte();

            int totalSegmentSize = 0;
            for (int i = 0; i < numSegments; i++) {
                totalSegmentSize += dis.readUnsignedByte();
            }

            if ((headerType & 0x02) != 0 && pageSeq == 0) {
                byte[] pageData = new byte[totalSegmentSize];
                dis.readFully(pageData);
                if (pageData.length >= 16 && pageData[0] == 0x01) {
                    int sr = (pageData[12] & 0xFF)
                            | ((pageData[13] & 0xFF) << 8)
                            | ((pageData[14] & 0xFF) << 16)
                            | ((pageData[15] & 0xFF) << 24);
                    if (sr > 0) sampleRate = sr;
                }
            } else {
                skipFully(dis, totalSegmentSize); // skipBytes exits early if skip() returns 0
            }

            if ((headerType & 0x04) != 0) break;
        }

        if (granulePosition <= 0 || sampleRate <= 0) return -1;
        return (granulePosition * 1000) / sampleRate;
    }

    private static void skipFully(InputStream is, int n) throws IOException {
        byte[] buf = new byte[Math.min(n, 4096)];
        int remaining = n;
        while (remaining > 0) {
            int read = is.read(buf, 0, Math.min(remaining, buf.length));
            if (read < 0) break;
            remaining -= read;
        }
    }

    private static long readInt64Le(DataInputStream dis) throws IOException {
        return (dis.readByte() & 0xFFL)
                | ((dis.readByte() & 0xFFL) << 8)
                | ((dis.readByte() & 0xFFL) << 16)
                | ((dis.readByte() & 0xFFL) << 24)
                | ((dis.readByte() & 0xFFL) << 32)
                | ((dis.readByte() & 0xFFL) << 40)
                | ((dis.readByte() & 0xFFL) << 48)
                | ((dis.readByte() & 0xFFL) << 56);
    }

    private static int readInt32Le(DataInputStream dis) throws IOException {
        return (dis.readByte() & 0xFF)
                | ((dis.readByte() & 0xFF) << 8)
                | ((dis.readByte() & 0xFF) << 16)
                | ((dis.readByte() & 0xFF) << 24);
    }
}
