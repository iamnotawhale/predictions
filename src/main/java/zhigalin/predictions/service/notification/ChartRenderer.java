package zhigalin.predictions.service.notification;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
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

    private static final Color[] SERIES = {
            new Color(157, 123, 240),
            new Color(92, 184, 122),
            new Color(232, 168, 64),
            new Color(91, 155, 213),
            new Color(212, 96, 74),
            new Color(46, 207, 192)
    };

    private final PredictionService predictionService;
    private final HtmlImageRenderer htmlImages;
    private final PanicSender panicSender;

    public ChartRenderer(PredictionService predictionService, HtmlImageRenderer htmlImages, PanicSender panicSender) {
        this.predictionService = predictionService;
        this.htmlImages = htmlImages;
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
                    null, null, null,
                    dataset, PlotOrientation.VERTICAL, true, false, false);

            chart.setBackgroundPaint(new Color(20, 17, 26));
            chart.setBorderVisible(false);

            CategoryPlot plot = chart.getCategoryPlot();
            plot.setBackgroundPaint(new Color(28, 24, 36));
            plot.setOutlineVisible(false);
            plot.setDomainGridlinesVisible(true);
            plot.setRangeGridlinesVisible(true);
            plot.setDomainGridlinePaint(new Color(255, 255, 255, 28));
            plot.setRangeGridlinePaint(new Color(255, 255, 255, 28));

            LineAndShapeRenderer renderer = new LineAndShapeRenderer();
            for (int i = 0; i < dataset.getRowCount(); i++) {
                renderer.setSeriesPaint(i, SERIES[i % SERIES.length]);
                renderer.setSeriesStroke(i, new BasicStroke(2.8f));
                renderer.setSeriesShapesVisible(i, true);
                renderer.setSeriesShape(i, new Ellipse2D.Double(-3.5, -3.5, 7, 7));
            }
            plot.setRenderer(renderer);

            Font axisFont = new Font("SansSerif", Font.PLAIN, 16);
            plot.getDomainAxis().setLabelPaint(new Color(154, 143, 176));
            plot.getDomainAxis().setTickLabelPaint(new Color(232, 223, 245));
            plot.getRangeAxis().setLabelPaint(new Color(154, 143, 176));
            plot.getRangeAxis().setTickLabelPaint(new Color(232, 223, 245));
            plot.getDomainAxis().setTickLabelFont(axisFont);
            plot.getRangeAxis().setTickLabelFont(axisFont);
            if (plot.getRangeAxis() instanceof org.jfree.chart.axis.NumberAxis numberAxis) {
                numberAxis.setAutoRangeIncludesZero(true);
                numberAxis.setAutoTickUnitSelection(true);
                numberAxis.setMinorTickCount(4);
                numberAxis.setMinorTickMarksVisible(true);
            }
            if (chart.getLegend() != null) {
                chart.getLegend().setBackgroundPaint(new Color(20, 17, 26));
                chart.getLegend().setItemPaint(new Color(232, 223, 245));
                chart.getLegend().setItemFont(new Font("SansSerif", Font.BOLD, 14));
            }

            BufferedImage chartImage = chart.createBufferedImage(920, 720);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(chartImage, "png", baos);
            return htmlImages.createChartImage(baos.toByteArray());
        } catch (Exception e) {
            panicSender.sendPanic("Error creating chart", e);
            return null;
        }
    }
}
