package com.ocr.javafx.controller.components;

import com.ocr.javafx.ApplicationContext;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import lombok.Setter;

import java.time.DayOfWeek;
import java.util.Collections;
import java.util.Map;

public class BarchartController {
    @FXML
    public CategoryAxis xAxis;

    @FXML
    public NumberAxis yAxis;

    @FXML
    private BarChart<String, Number> barchart;

    @Setter
    private ApplicationContext applicationContext;

    public void initChartAxes() {
        yAxis.setForceZeroInRange(true);
        yAxis.setAutoRanging(false);
        yAxis.setLowerBound(0);
        yAxis.setUpperBound(8);
        yAxis.setMinorTickVisible(false);
        yAxis.setTickUnit(2);

        barchart.setCategoryGap(10);
        barchart.setBarGap(4);
        barchart.setPadding(Insets.EMPTY);

        barchart.setAnimated(false);
        barchart.setLegendVisible(false);
        xAxis.setLabel("");
        yAxis.setLabel("");
    }

    public void setupBarchart() {
        initChartAxes();
        Long userId = applicationContext
                .getSessionManager()
                .getCurrentUser()
                .getId();

        Map<DayOfWeek, Double> data =
                applicationContext
                        .getLearningChartService()
                        .getWeeklyLearningData(userId);
        applyData(data);
    }

    public void applyData(Map<DayOfWeek, Double> data) {
        if (data == null) {
            data = Collections.emptyMap();
        }
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Weekly Learning Hours");

        for (DayOfWeek day : DayOfWeek.values()) {
            Double value = data.getOrDefault(day, 0.0);
            series.getData().add(
                    new XYChart.Data<>(day.name().toUpperCase().substring(0, 3), value)
            );
        }

        barchart.getData().clear();
        barchart.getData().add(series);
    }
}
