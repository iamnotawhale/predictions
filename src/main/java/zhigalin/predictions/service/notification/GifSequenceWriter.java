package zhigalin.predictions.service.notification;

import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;

final class GifSequenceWriter implements AutoCloseable {

    private final ImageWriter writer;
    private final ImageWriteParam params;
    private final IIOMetadata metadata;
    private boolean started;

    GifSequenceWriter(ImageOutputStream output, int imageType, int delayCs, boolean loopContinuously)
            throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersBySuffix("gif");
        if (!writers.hasNext()) {
            throw new IOException("No GIF ImageWriter available");
        }
        writer = writers.next();
        params = writer.getDefaultWriteParam();
        ImageTypeSpecifier type = ImageTypeSpecifier.createFromBufferedImageType(imageType);
        metadata = writer.getDefaultImageMetadata(type, params);
        String metaFormat = metadata.getNativeMetadataFormatName();
        IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(metaFormat);

        IIOMetadataNode gce = getNode(root, "GraphicControlExtension");
        gce.setAttribute("disposalMethod", "restoreToBackgroundColor");
        gce.setAttribute("userInputFlag", "FALSE");
        gce.setAttribute("transparentColorFlag", "FALSE");
        gce.setAttribute("delayTime", Integer.toString(Math.max(2, delayCs)));
        gce.setAttribute("transparentColorIndex", "0");

        IIOMetadataNode appExtensions = getNode(root, "ApplicationExtensions");
        IIOMetadataNode app = new IIOMetadataNode("ApplicationExtension");
        app.setAttribute("applicationID", "NETSCAPE");
        app.setAttribute("authenticationCode", "2.0");
        int loop = loopContinuously ? 0 : 1;
        app.setUserObject(new byte[]{0x1, (byte) (loop & 0xff), (byte) ((loop >> 8) & 0xff)});
        appExtensions.appendChild(app);

        metadata.setFromTree(metaFormat, root);
        writer.setOutput(output);
        writer.prepareWriteSequence(null);
        started = true;
    }

    void writeToSequence(RenderedImage img) throws IOException {
        writer.writeToSequence(new IIOImage(img, null, metadata), params);
    }

    @Override
    public void close() throws IOException {
        if (started) {
            writer.endWriteSequence();
            started = false;
        }
        writer.dispose();
    }

    private static IIOMetadataNode getNode(IIOMetadataNode root, String name) {
        for (int i = 0; i < root.getLength(); i++) {
            if (root.item(i).getNodeName().equalsIgnoreCase(name)) {
                return (IIOMetadataNode) root.item(i);
            }
        }
        IIOMetadataNode node = new IIOMetadataNode(name);
        root.appendChild(node);
        return node;
    }
}
