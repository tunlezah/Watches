// Renders the iOS and Mac app icons from the Android launcher icon, so every device shows the same artwork.
//
//   java tools/RenderIosAppIcon.java        (from the repository root, with JDK 21)
//
// Reads androidApp/src/main/res/drawable/ic_launcher_foreground.xml and the icon_background colour,
// and writes:
//   iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/AppIcon.png   1024 px square, opaque
//   desktopApp/icons/Watches.icns                                  the Mac icon, at every size macOS asks for

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class RenderIosAppIcon {
    static final int SIZE = 1024;
    // The Android canvas is 108 units with the art inside its central 66. iOS shows the whole square,
    // so the central 80 units fill it, which leaves the margin iOS icons usually have.
    static final double VIEW = 80;
    static final double OFFSET = (108 - VIEW) / 2;

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        Path res = Path.of("androidApp/src/main/res");
        Document vector = parse(res.resolve("drawable/ic_launcher_foreground.xml"));
        Color background = colour(resourceColour(parse(res.resolve("values/themes.xml")), "icon_background"));

        // No alpha channel: App Store Connect refuses icons with transparency.
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = graphics(image);
        g.setColor(background);
        g.fillRect(0, 0, SIZE, SIZE);
        g.scale(SIZE / VIEW, SIZE / VIEW);
        g.translate(-OFFSET, -OFFSET);
        paint(g, vector);
        g.dispose();

        Path out = Path.of("iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/AppIcon.png");
        Files.createDirectories(out.getParent());
        ImageIO.write(image, "png", out.toFile());
        System.out.println("Wrote " + out);

        Path mac = Path.of("desktopApp/icons/Watches.icns");
        Files.createDirectories(mac.getParent());
        Files.write(mac, icns(vector, background));
        System.out.println("Wrote " + mac);
    }

    static Graphics2D graphics(BufferedImage image) {
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        return g;
    }

    /** Draws the launcher artwork, in the Android vector's own units. */
    static void paint(Graphics2D g, Document vector) {
        NodeList paths = vector.getElementsByTagName("path");
        for (int i = 0; i < paths.getLength(); i++) {
            Element p = (Element) paths.item(i);
            Shape shape = svgPath(p.getAttribute("android:pathData"));
            String fill = p.getAttribute("android:fillColor");
            if (!fill.isEmpty()) {
                g.setColor(colour(fill));
                g.fill(shape);
            }
            String stroke = p.getAttribute("android:strokeColor");
            if (!stroke.isEmpty()) {
                int cap = switch (p.getAttribute("android:strokeLineCap")) {
                    case "round" -> BasicStroke.CAP_ROUND;
                    case "square" -> BasicStroke.CAP_SQUARE;
                    default -> BasicStroke.CAP_BUTT;
                };
                g.setColor(colour(stroke));
                g.setStroke(new BasicStroke(Float.parseFloat(p.getAttribute("android:strokeWidth")), cap, BasicStroke.JOIN_MITER));
                g.draw(shape);
            }
        }
    }

    // A Mac icon isn't masked by the system the way a phone's is: the file carries its own shape. Apple's
    // grid puts a rounded square of 824 px, with corners of about 185 px, in a 1024 px canvas, and the
    // rest is transparent. The artwork is drawn afresh at each size rather than scaled down from one.
    static final double MAC_PLATE = 824.0 / 1024.0;
    static final double MAC_CORNER = 185.4 / 1024.0;

    static BufferedImage macIcon(Document vector, Color background, int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = graphics(image);
        double plate = size * MAC_PLATE;
        double inset = (size - plate) / 2;
        Shape shape = new RoundRectangle2D.Double(inset, inset, plate, plate, size * MAC_CORNER * 2, size * MAC_CORNER * 2);
        g.setColor(background);
        g.fill(shape);
        g.clip(shape);
        g.translate(inset, inset);
        g.scale(plate / VIEW, plate / VIEW);
        g.translate(-OFFSET, -OFFSET);
        paint(g, vector);
        g.dispose();
        return image;
    }

    /** An .icns file: a header, then each size as a PNG under the four-letter name macOS knows it by. */
    static byte[] icns(Document vector, Color background) throws Exception {
        String[] types = {"icp4", "icp5", "ic11", "ic12", "ic07", "ic13", "ic08", "ic14", "ic09", "ic10"};
        int[] sizes = {16, 32, 32, 64, 128, 256, 256, 512, 512, 1024};
        ByteArrayOutputStream entries = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(entries);
        for (int i = 0; i < types.length; i++) {
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(macIcon(vector, background, sizes[i]), "png", png);
            data.writeBytes(types[i]);
            data.writeInt(png.size() + 8);
            png.writeTo(data);
        }
        ByteArrayOutputStream file = new ByteArrayOutputStream();
        DataOutputStream header = new DataOutputStream(file);
        header.writeBytes("icns");
        header.writeInt(entries.size() + 8);
        entries.writeTo(header);
        return file.toByteArray();
    }

    static Document parse(Path file) throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file.toFile());
    }

    static String resourceColour(Document resources, String name) {
        NodeList colours = resources.getElementsByTagName("color");
        for (int i = 0; i < colours.getLength(); i++) {
            Element c = (Element) colours.item(i);
            if (c.getAttribute("name").equals(name)) return c.getTextContent().trim();
        }
        throw new IllegalArgumentException("No colour " + name);
    }

    /** #RRGGBB or #AARRGGBB. */
    static Color colour(String hex) {
        long v = Long.parseLong(hex.substring(1), 16);
        return hex.length() == 9 ? new Color((int) v, true) : new Color((int) v);
    }

    /** The subset of SVG path syntax vector drawables use here: M, L, H, V, A and Z, absolute or relative. */
    static Shape svgPath(String data) {
        List<String> tokens = new ArrayList<>();
        Matcher m = Pattern.compile("[MmLlHhVvAaZz]|-?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?").matcher(data);
        while (m.find()) tokens.add(m.group());

        Path2D.Double path = new Path2D.Double();
        double x = 0, y = 0, startX = 0, startY = 0;
        char command = 'M';
        int i = 0;
        while (i < tokens.size()) {
            String t = tokens.get(i);
            if (Character.isLetter(t.charAt(0))) {
                command = t.charAt(0);
                i++;
                if (command == 'Z' || command == 'z') {
                    path.closePath();
                    x = startX;
                    y = startY;
                    continue;
                }
            }
            boolean relative = Character.isLowerCase(command);
            switch (Character.toUpperCase(command)) {
                case 'M' -> {
                    x = num(tokens, i++) + (relative ? x : 0);
                    y = num(tokens, i++) + (relative ? y : 0);
                    path.moveTo(x, y);
                    startX = x;
                    startY = y;
                    command = relative ? 'l' : 'L'; // further pairs after a move are lines
                }
                case 'L' -> {
                    x = num(tokens, i++) + (relative ? x : 0);
                    y = num(tokens, i++) + (relative ? y : 0);
                    path.lineTo(x, y);
                }
                case 'H' -> {
                    x = num(tokens, i++) + (relative ? x : 0);
                    path.lineTo(x, y);
                }
                case 'V' -> {
                    y = num(tokens, i++) + (relative ? y : 0);
                    path.lineTo(x, y);
                }
                case 'A' -> {
                    double rx = num(tokens, i++), ry = num(tokens, i++), angle = num(tokens, i++);
                    boolean large = num(tokens, i++) != 0, sweep = num(tokens, i++) != 0;
                    double ex = num(tokens, i++) + (relative ? x : 0);
                    double ey = num(tokens, i++) + (relative ? y : 0);
                    arc(path, x, y, rx, ry, angle, large, sweep, ex, ey);
                    x = ex;
                    y = ey;
                }
                default -> throw new IllegalArgumentException("Unsupported path command " + command);
            }
        }
        return path;
    }

    static double num(List<String> tokens, int i) {
        return Double.parseDouble(tokens.get(i));
    }

    /** An SVG elliptical arc, converted to its centre form (SVG 1.1 implementation notes, F.6.5). */
    static void arc(Path2D path, double x0, double y0, double rx, double ry, double angle, boolean large, boolean sweep, double x, double y) {
        if (rx == 0 || ry == 0) {
            path.lineTo(x, y);
            return;
        }
        rx = Math.abs(rx);
        ry = Math.abs(ry);
        double phi = Math.toRadians(angle % 360);
        double cos = Math.cos(phi), sin = Math.sin(phi);
        double dx = (x0 - x) / 2, dy = (y0 - y) / 2;
        double x1 = cos * dx + sin * dy, y1 = -sin * dx + cos * dy;
        double lambda = x1 * x1 / (rx * rx) + y1 * y1 / (ry * ry);
        if (lambda > 1) {
            rx *= Math.sqrt(lambda);
            ry *= Math.sqrt(lambda);
        }
        double num = rx * rx * ry * ry - rx * rx * y1 * y1 - ry * ry * x1 * x1;
        double den = rx * rx * y1 * y1 + ry * ry * x1 * x1;
        double coef = (large == sweep ? -1 : 1) * Math.sqrt(Math.max(0, num / den));
        double cx1 = coef * rx * y1 / ry, cy1 = -coef * ry * x1 / rx;
        double cx = cos * cx1 - sin * cy1 + (x0 + x) / 2;
        double cy = sin * cx1 + cos * cy1 + (y0 + y) / 2;
        double start = angle(1, 0, (x1 - cx1) / rx, (y1 - cy1) / ry);
        double extent = angle((x1 - cx1) / rx, (y1 - cy1) / ry, (-x1 - cx1) / rx, (-y1 - cy1) / ry);
        if (!sweep && extent > 0) extent -= 2 * Math.PI;
        if (sweep && extent < 0) extent += 2 * Math.PI;
        // Arc2D's angles grow towards the top of the screen; SVG's grow towards the bottom.
        Arc2D.Double a = new Arc2D.Double(cx - rx, cy - ry, 2 * rx, 2 * ry, -Math.toDegrees(start), -Math.toDegrees(extent), Arc2D.OPEN);
        path.append(AffineTransform.getRotateInstance(phi, cx, cy).createTransformedShape(a), true);
    }

    static double angle(double ux, double uy, double vx, double vy) {
        double a = Math.acos(Math.max(-1, Math.min(1, (ux * vx + uy * vy) / (Math.hypot(ux, uy) * Math.hypot(vx, vy)))));
        return ux * vy - uy * vx < 0 ? -a : a;
    }
}
