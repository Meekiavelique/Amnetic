package com.meekdev.amnetic.client.content;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;

final class ImageProbeRegression {

    private static final int HUGE = 100_000;

    static void verify() throws IOException {
        Hostile.refused("png claiming 100000 by 100000", "larger than", () -> ImageProbe.decode(png(HUGE, HUGE), ImageProbe.TEXTURES));
        Hostile.refused("png header claiming 100000 by 100000", "larger than", () -> ImageProbe.header(png(HUGE, HUGE), ImageProbe.TEXTURES));
        Hostile.refused("png claiming 0 by 5", "width", () -> ImageProbe.header(png(0, 5), ImageProbe.TEXTURES));
        Hostile.refused("tiff", "not an image", () -> ImageProbe.decode(new byte[] {'I', 'I', '*', 0, 8, 0, 0, 0}, ImageProbe.TEXTURES));
        Hostile.refused("gif where only textures go", "cannot be used here",
                () -> ImageProbe.decode("GIF89a\u0001\u0000\u0001\u0000".getBytes(StandardCharsets.ISO_8859_1), ImageProbe.TEXTURES));

        BufferedImage small = new BufferedImage(3, 2, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        ImageIO.write(small, "png", encoded);
        BufferedImage back = ImageProbe.decode(encoded.toByteArray(), ImageProbe.TEXTURES);
        Hostile.require(back.getWidth() == 3 && back.getHeight() == 2, "a real png decodes");
        System.out.println("PASS: a real png decodes");
    }

    // a png that is only its signature, a header claiming width by height, and its end
    static byte[] png(int width, int height) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.write(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'});
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        DataOutputStream fields = new DataOutputStream(header);
        fields.writeInt(width);
        fields.writeInt(height);
        fields.write(new byte[] {8, 6, 0, 0, 0});
        chunk(out, "IHDR", header.toByteArray());
        chunk(out, "IEND", new byte[0]);
        return bytes.toByteArray();
    }

    private static void chunk(DataOutputStream out, String type, byte[] data) throws IOException {
        byte[] tag = type.getBytes(StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32();
        crc.update(tag);
        crc.update(data);
        out.writeInt(data.length);
        out.write(tag);
        out.write(data);
        out.writeInt((int) crc.getValue());
    }
}
