package com.meekdev.amnetic.client.content;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

public final class ImageProbe {
    public enum Format {
        PNG("png"), JPEG("jpeg"), WEBP("webp"), GIF("gif"), BMP("bmp");

        private final String reader;

        Format(String reader) {
            this.reader = reader;
        }
    }

    public record Header(Format format, int width, int height) {}

    public static final Set<Format> TEXTURES = Set.of(Format.PNG, Format.JPEG, Format.WEBP);

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};
    private static final byte[] JPEG = {(byte) 0xff, (byte) 0xd8, (byte) 0xff};
    private static final byte[] RIFF = {'R', 'I', 'F', 'F'};
    private static final byte[] WEBP = {'W', 'E', 'B', 'P'};
    private static final int WEBP_TAG_AT = 8;
    private static final byte[] GIF87 = {'G', 'I', 'F', '8', '7', 'a'};
    private static final byte[] GIF89 = {'G', 'I', 'F', '8', '9', 'a'};
    private static final byte[] BMP = {'B', 'M'};

    private ImageProbe() {}

    public static Header header(byte[] bytes, Set<Format> allowed) throws IOException {
        Format format = format(bytes, allowed);
        return read(bytes, format, reader -> checked(format, reader));
    }

    public static BufferedImage decode(byte[] bytes, Set<Format> allowed) throws IOException {
        Format format = format(bytes, allowed);
        return read(bytes, format, reader -> {
            checked(format, reader);
            return reader.read(0);
        });
    }

    public static void checkSize(int width, int height) {
        Bounded.require(width > 0 && height > 0, "an image of " + width + " by " + height + " pixels is empty");
        Bounded.require(width <= ContentLimits.IMAGE_SIDE && height <= ContentLimits.IMAGE_SIDE
                        && (long) width * height <= ContentLimits.IMAGE_PIXELS,
                "an image of " + width + " by " + height + " pixels is larger than " + ContentLimits.IMAGE_SIDE
                        + " a side or " + ContentLimits.IMAGE_PIXELS + " pixels");
    }

    private static Format format(byte[] bytes, Set<Format> allowed) {
        Format format = sniff(bytes);
        if (format == null) throw new RejectedContentException("not an image this game can use");
        if (!allowed.contains(format)) throw new RejectedContentException("a " + format.reader + " image cannot be used here");
        return format;
    }

    private static Format sniff(byte[] bytes) {
        if (starts(bytes, 0, PNG)) return Format.PNG;
        if (starts(bytes, 0, JPEG)) return Format.JPEG;
        if (starts(bytes, 0, RIFF) && starts(bytes, WEBP_TAG_AT, WEBP)) return Format.WEBP;
        if (starts(bytes, 0, GIF87) || starts(bytes, 0, GIF89)) return Format.GIF;
        if (starts(bytes, 0, BMP)) return Format.BMP;
        return null;
    }

    private static boolean starts(byte[] bytes, int at, byte[] magic) {
        return bytes.length >= at + magic.length && Arrays.equals(bytes, at, at + magic.length, magic, 0, magic.length);
    }

    private static Header checked(Format format, ImageReader reader) throws IOException {
        int width = reader.getWidth(0);
        int height = reader.getHeight(0);
        checkSize(width, height);
        return new Header(format, width, height);
    }

    private interface ReaderUse<T> {
        T apply(ImageReader reader) throws IOException;
    }

    private static <T> T read(byte[] bytes, Format format, ReaderUse<T> use) throws IOException {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(format.reader);
        if (!readers.hasNext()) throw new RejectedContentException("this game cannot read " + format.reader + " images");
        ImageReader reader = readers.next();
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            reader.setInput(in, true, true);
            return use.apply(reader);
        } finally {
            reader.dispose();
        }
    }
}
