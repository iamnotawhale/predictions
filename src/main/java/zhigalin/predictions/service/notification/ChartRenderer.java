package zhigalin.predictions.service.notification;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.LineAndShapeRenderer;
import org.jfree.data.category.DefaultCategoryDataset;
import org.springframework.stereotype.Service;
import zhigalin.predictions.panic.PanicSender;
import zhigalin.predictions.service.predict.PredictionService;

@Service
public class ChartRenderer {

    private final PredictionService predictionService;
    private final ImageRenderer images;
    private final PanicSender panicSender;

    public ChartRenderer(PredictionService predictionService, ImageRenderer images, PanicSender panicSender) {
        this.predictionService = predictionService;
        this.images = images;
        this.panicSender = panicSender;
    }

    public String createTotalPointsChartImage() {
        try {
            Map<String, Map<Integer, Integer>> allUsersData = predictionService.getAllUsersCumulativePoints().entrySet().stream()
                    .sorted((e1, e2) -> {
                        int m1 = Collections.max(e1.getValue().values());
                        int m2 = Collections.max(e2.getValue().values());
                        return Integer.compare(m2, m1);
                    })
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));

            DefaultCategoryDataset dataset = new DefaultCategoryDataset();
            for (Map.Entry<String, Map<Integer, Integer>> userEntry : allUsersData.entrySet()) {
                String login = userEntry.getKey();
                for (Map.Entry<Integer, Integer> weekEntry : userEntry.getValue().entrySet()) {
                    dataset.addValue(weekEntry.getValue(), login.toUpperCase().substring(0, 3), weekEntry.getKey());
                }
            }

            JFreeChart chart = ChartFactory.createLineChart(
                    "ГРАФИК НАБОРА ОЧКОВ ПО ТУРАМ", "Week", "PTS",
                    dataset, PlotOrientation.VERTICAL, true, true, false);

            chart.setBackgroundPaint(new Color(0, 0, 0, 0));
            chart.setBackgroundImageAlpha(0f);

            CategoryPlot plot = chart.getCategoryPlot();
            plot.setBackgroundPaint(new Color(255, 255, 255, 160));
            plot.setOutlineVisible(false);
            plot.setDomainGridlinesVisible(true);
            plot.setRangeGridlinesVisible(true);
            plot.setDomainGridlinePaint(Color.WHITE);
            plot.setRangeGridlinePaint(Color.WHITE);

            LineAndShapeRenderer renderer = new LineAndShapeRenderer();
            for (int i = 0; i < dataset.getRowCount(); i++) {
                renderer.setSeriesPaint(i, getSeriesColor(i));
                renderer.setSeriesStroke(i, new BasicStroke(2.5f));
                renderer.setSeriesShapesVisible(i, true);
                renderer.setSeriesShape(i, new Ellipse2D.Double(-3, -3, 6, 6));
            }
            plot.setRenderer(renderer);

            chart.getTitle().setFont(new Font("Arial", Font.BOLD, 40));
            chart.getTitle().setPaint(Color.WHITE);
            plot.getDomainAxis().setLabelPaint(Color.WHITE);
            plot.getDomainAxis().setTickLabelPaint(Color.WHITE);
            plot.getRangeAxis().setLabelPaint(Color.WHITE);
            plot.getRangeAxis().setTickLabelPaint(Color.WHITE);

            plot.getDomainAxis().setLabelFont(new Font("Arial", Font.BOLD, 14));
            plot.getRangeAxis().setLabelFont(new Font("Arial", Font.BOLD, 14));

            BufferedImage background = images.generateWithBackground(ImageRenderer.WIDTH, ImageRenderer.HEIGHT, ImageRenderer.BACKGROUND_COLOR);
            BufferedImage chartImage = chart.createBufferedImage(
                    (int) (background.getWidth() * 0.8),
                    (int) (background.getHeight() * 0.8)
            );

            Graphics2D g = background.createGraphics();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
            g.drawImage(chartImage, (int) (background.getWidth() * 0.1), (int) (background.getHeight() * 0.1), null);
            g.dispose();

            File out = File.createTempFile("charts", ".png");
            ImageIO.write(background, "png", out);
            return out.getAbsolutePath();
        } catch (Exception e) {
            panicSender.sendPanic("Error creating chart", e);
            return null;
        }
    }

    private static Color getSeriesColor(int index) {
        Color[] colors = {Color.blue, Color.green, Color.red, Color.yellow};
        return colors[index % colors.length];
    }
}
