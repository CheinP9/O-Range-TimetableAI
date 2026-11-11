package com.ocr.javafx.controller.components;

import com.ocr.javafx.ApplicationContext;
import javafx.fxml.FXML;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Tooltip;
import lombok.Setter;

import java.util.Collections;
import java.util.Map;

public class PiechartController {

    @FXML
    private PieChart piechart;

    @Setter
    private ApplicationContext applicationContext;

    public void initChartLayout() {
        piechart.setAnimated(false);
        piechart.setLabelsVisible(true);
        piechart.setLegendVisible(false);
        piechart.setClockwise(true);
        piechart.setStartAngle(90);

        piechart.setMinSize(360, 360);
        piechart.setPrefSize(400, 400);
        piechart.setMaxSize(400, 400);
    }

    public void setupPieChart() {
        initChartLayout();
        Long userId = applicationContext
                .getSessionManager()
                .getCurrentUser()
                .getId();

        Map<String, Double> data = applicationContext
                .getLearningChartService()
                .getSkillDistribution(userId);
        applyData(data);
    }

    public void applyData(Map<String, Double> data) {
        if (data == null) {
            data = Collections.emptyMap();
        }
        piechart.getData().clear();

        for (Map.Entry<String, Double> entry : data.entrySet()) {
            PieChart.Data slice = new PieChart.Data(
                    entry.getKey(),
                    entry.getValue()
            );

            piechart.getData().add(slice);

            slice.nodeProperty().addListener((obs, oldNode, node) -> {
                if (node != null) {
                    Tooltip.install(
                            node,
                            new Tooltip(entry.getKey() + ": " + entry.getValue())
                    );
                }
            });
        }
    }
}
