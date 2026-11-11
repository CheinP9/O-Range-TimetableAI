package com.ocr.javafx.controller.components;


import com.ocr.javafx.dto.LearningPlanDTO;
import com.ocr.javafx.service.LearningPlanService;
import com.ocr.javafx.util.Function.AlertUtils;
import javafx.animation.*;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.Optional;

public class PlanCardController {
    @FXML
    private VBox rootVBox;
    @FXML
    private Label lblTitle;
    @FXML
    private Label lblGoal;
    @FXML
    private Label lblProgressPercent;
    @FXML
    private ProgressBar progressBar;
    @FXML
    private Label lblDuration;
    @FXML
    private Label lblRemaining;
    @FXML
    private Label lblIntensity;
    @FXML
    private FlowPane flowPaneSkills;
    @FXML
    private Button btnViewDetails;
    @FXML
    private Button btnEditGoal;


    private LearningPlanDTO currentPlan;
    private LearningPlanService planService;
    private Long planId;
    private Runnable onDeleteCallback;
    private Runnable onViewDetailsCallback;
    private Runnable onEditCallback;


    public void setPlanData(LearningPlanDTO plan, LearningPlanService planService, Long planId, Runnable onDelete, Runnable onViewDetails) {
        setPlanData(plan, planService, planId, onDelete, onViewDetails, null);
    }

    public void setPlanData(LearningPlanDTO plan, LearningPlanService planService, Long planId, Runnable onDelete, Runnable onViewDetails, Runnable onEditPlan) {
        this.currentPlan = plan;
        this.planId = plan.getId();
        this.planService = planService;
        this.onDeleteCallback = onDelete;
        this.onViewDetailsCallback = onViewDetails;
        this.onEditCallback = onEditPlan;


        lblTitle.setText(plan.getTitle());
        lblGoal.setText(plan.getGoal() != null && !plan.getGoal().isBlank() ? plan.getGoal() : "Chưa có mô tả mục tiêu.");
        lblDuration.setText(plan.getDurationDays() + " days");
        lblRemaining.setText(plan.getRemainingDays() + " days");


        int targetPercent = plan.getProgress() != null ? plan.getProgress() : 0;
        double targetProgress = targetPercent / 100.0;
        int completedSessions = plan.getCompletedSessions() != null ? plan.getCompletedSessions() : 0;
        int targetSessions = (plan.getTargetSessions() != null && plan.getTargetSessions() > 0)
                ? plan.getTargetSessions()
                : (plan.getDurationDays() != null ? plan.getDurationDays() : 10);

        progressBar.setProgress(0);
        lblProgressPercent.setText("0% (" + completedSessions + "/" + targetSessions + " buổi)");


        lblIntensity.getStyleClass().removeAll("intensity-high", "intensity-medium", "intensity-low");


        lblIntensity.setText(plan.getIntensity() + " Intensity");


        if ("High".equalsIgnoreCase(plan.getIntensity())) {
            lblIntensity.setStyle("-fx-background-color: #FEE2E2; -fx-text-fill: #EF4444;");
        } else if ("Medium".equalsIgnoreCase(plan.getIntensity())) {
            lblIntensity.setStyle("-fx-background-color: #FEF3C7; -fx-text-fill: #D97706;");
        } else {
            lblIntensity.setStyle("-fx-background-color: #DCFCE7; -fx-text-fill: #22C55E;");
        }


        flowPaneSkills.getChildren().clear();
        if (plan.getSkills() != null && !plan.getSkills().isEmpty()) {
            for (String skill : plan.getSkills()) {
                Label skillLabel = new Label(skill);
                skillLabel.setStyle("-fx-background-color: #EFF6FF; -fx-text-fill: #3B82F6; -fx-padding: 4 8; -fx-background-radius: 4; -fx-font-size: 12px;");
                flowPaneSkills.getChildren().add(skillLabel);
            }
        } else {
            if (plan.getDomain() != null && !plan.getDomain().isBlank()) {
                Label domainChip = new Label("🏷 " + plan.getDomain());
                domainChip.setStyle("-fx-background-color: #EFF6FF; -fx-text-fill: #2563EB; -fx-padding: 4 8; -fx-background-radius: 6; -fx-font-size: 12px; -fx-font-weight: bold;");
                flowPaneSkills.getChildren().add(domainChip);
            }
        }
        Label sessionChip = new Label("📚 Mục tiêu: " + completedSessions + "/" + targetSessions + " buổi học ✏");
        sessionChip.setStyle("-fx-background-color: #FFF7ED; -fx-text-fill: #EA580C; -fx-padding: 4 8; -fx-background-radius: 6; -fx-font-size: 12px; -fx-font-weight: bold;");
        sessionChip.setCursor(Cursor.HAND);
        sessionChip.setTooltip(new Tooltip("Bấm để chỉnh sửa mục tiêu và số buổi yêu cầu của kế hoạch này"));
        sessionChip.setOnMouseClicked(e -> triggerEditPlan());
        flowPaneSkills.getChildren().add(sessionChip);

        playEntranceAnimation(targetProgress, targetPercent, completedSessions, targetSessions);
        btnViewDetails.setOnAction(e -> {
            if (onViewDetails != null) {
                onViewDetails.run();
            }
        });
    }


    private void playEntranceAnimation(double targetProgress, int targetPercent, int completedSessions, int targetSessions) {
        ScaleTransition scale = new ScaleTransition(Duration.millis(300), rootVBox);
        scale.setFromX(0.8);
        scale.setFromY(0.8);
        scale.setToX(1.0);
        scale.setToY(1.0);


        FadeTransition fade = new FadeTransition(Duration.millis(300), rootVBox);
        fade.setFromValue(0.0);
        fade.setToValue(1.0);


        Timeline progressTimeline = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(progressBar.progressProperty(), 0)),
                new KeyFrame(Duration.millis(1000), new KeyValue(progressBar.progressProperty(), targetProgress, Interpolator.EASE_BOTH))
        );


        DoubleProperty progressDouble = new SimpleDoubleProperty(0);
        progressDouble.addListener((obs, oldVal, newVal) -> {
            int pct = (int) Math.round(newVal.doubleValue() * 100);
            lblProgressPercent.setText(String.format("%d%% (%d/%d buổi)", pct, completedSessions, targetSessions));
        });


        Timeline labelTimeline = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(progressDouble, 0)),
                new KeyFrame(Duration.millis(1000), new KeyValue(progressDouble, targetProgress, Interpolator.EASE_BOTH))
        );


        ParallelTransition parallel = new ParallelTransition(scale, fade, progressTimeline, labelTimeline);
        parallel.play();
    }


    @FXML
    private void handleEditGoal(ActionEvent event) {
        triggerEditPlan();
    }

    private void triggerEditPlan() {
        if (onEditCallback != null) {
            onEditCallback.run();
        } else {
            openEditGoalDialog();
        }
    }


    private void openEditGoalDialog() {
        if (currentPlan == null || planService == null) {
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Chỉnh sửa mục tiêu kế hoạch học tập");
        dialog.setHeaderText("Cập nhật mục tiêu & thông số cho: " + currentPlan.getTitle());

        ButtonType saveBtnType = new ButtonType("Lưu thay đổi", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtnType, ButtonType.CANCEL);

        var cssUrl = getClass().getResource("/com/ocr/javafx/style/timetable.css");
        if (cssUrl != null) {
            dialog.getDialogPane().getStylesheets().add(cssUrl.toExternalForm());
            dialog.getDialogPane().getStyleClass().add("timetable-dialog-pane");
        }
        dialog.getDialogPane().setMinWidth(480);

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(12);
        grid.setPadding(new Insets(16, 16, 10, 16));

        ColumnConstraints colLabel = new ColumnConstraints();
        colLabel.setMinWidth(155);
        ColumnConstraints colInput = new ColumnConstraints();
        colInput.setHgrow(Priority.ALWAYS);
        colInput.setFillWidth(true);
        grid.getColumnConstraints().addAll(colLabel, colInput);

        TextField titleField = new TextField(currentPlan.getTitle() != null ? currentPlan.getTitle() : "");
        titleField.setPromptText("Tên kế hoạch học tập");

        TextField domainField = new TextField(currentPlan.getDomain() != null ? currentPlan.getDomain() : "");
        domainField.setPromptText("Lĩnh vực (VD: Backend Developer, Toán học...)");

        TextArea goalArea = new TextArea(currentPlan.getGoal() != null ? currentPlan.getGoal() : "");
        goalArea.setPromptText("Mô tả mục tiêu cụ thể bạn muốn đạt được với kế hoạch này...");
        goalArea.setPrefRowCount(3);
        goalArea.setWrapText(true);

        int initSessions = (currentPlan.getTargetSessions() != null && currentPlan.getTargetSessions() > 0)
                ? currentPlan.getTargetSessions()
                : (currentPlan.getDurationDays() != null ? currentPlan.getDurationDays() : 10);
        Spinner<Integer> targetSessionsSpinner = new Spinner<>(1, 365, initSessions);
        targetSessionsSpinner.setEditable(true);
        targetSessionsSpinner.setMaxWidth(Double.MAX_VALUE);

        int initDuration = (currentPlan.getDurationDays() != null && currentPlan.getDurationDays() > 0)
                ? currentPlan.getDurationDays()
                : 14;
        Spinner<Integer> durationDaysSpinner = new Spinner<>(1, 365, initDuration);
        durationDaysSpinner.setEditable(true);
        durationDaysSpinner.setMaxWidth(Double.MAX_VALUE);

        ComboBox<String> intensityCombo = new ComboBox<>(FXCollections.observableArrayList("Low", "Medium", "High"));
        intensityCombo.setValue(currentPlan.getIntensity() != null ? currentPlan.getIntensity() : "Medium");
        intensityCombo.setMaxWidth(Double.MAX_VALUE);

        int row = 0;
        grid.add(createFormLabel("Tên kế hoạch"), 0, row);
        grid.add(titleField, 1, row++);

        grid.add(createFormLabel("Lĩnh vực"), 0, row);
        grid.add(domainField, 1, row++);

        grid.add(createFormLabel("Mục tiêu đạt được"), 0, row);
        grid.add(goalArea, 1, row++);

        grid.add(createFormLabel("Số buổi mục tiêu (*)"), 0, row);
        grid.add(targetSessionsSpinner, 1, row++);

        grid.add(createFormLabel("Thời gian (Số ngày)"), 0, row);
        grid.add(durationDaysSpinner, 1, row++);

        grid.add(createFormLabel("Cường độ học"), 0, row);
        grid.add(intensityCombo, 1, row);

        dialog.getDialogPane().setContent(grid);

        Button saveBtn = (Button) dialog.getDialogPane().lookupButton(saveBtnType);
        if (saveBtn != null) {
            saveBtn.setStyle("-fx-background-color: #FF8C42; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8; -fx-padding: 8 18; -fx-cursor: hand;");
            saveBtn.addEventFilter(ActionEvent.ACTION, evt -> {
                commitSpinner(targetSessionsSpinner);
                commitSpinner(durationDaysSpinner);
                if (titleField.getText() == null || titleField.getText().trim().isEmpty()) {
                    evt.consume();
                    AlertUtils.showError("Vui lòng nhập Tên kế hoạch học tập.");
                }
            });
        }

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == saveBtnType) {
            commitSpinner(targetSessionsSpinner);
            commitSpinner(durationDaysSpinner);

            LearningPlanDTO updated = planService.updateLearningPlan(
                    this.planId,
                    titleField.getText(),
                    domainField.getText(),
                    goalArea.getText(),
                    durationDaysSpinner.getValue(),
                    targetSessionsSpinner.getValue(),
                    intensityCombo.getValue()
            );

            if (updated != null) {
                setPlanData(updated, this.planService, this.planId, this.onDeleteCallback, this.onViewDetailsCallback);
            } else {
                AlertUtils.showError("Không thể cập nhật mục tiêu kế hoạch học tập.");
            }
        }
    }

    private Label createFormLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-weight: 700; -fx-text-fill: #334155; -fx-font-size: 13px;");
        return label;
    }

    private void commitSpinner(Spinner<Integer> spinner) {
        if (spinner == null || !spinner.isEditable()) return;
        try {
            String text = spinner.getEditor().getText();
            if (text != null && !text.trim().isEmpty()) {
                int val = Integer.parseInt(text.trim());
                SpinnerValueFactory<Integer> vf = spinner.getValueFactory();
                if (vf instanceof SpinnerValueFactory.IntegerSpinnerValueFactory ivf) {
                    val = Math.max(ivf.getMin(), Math.min(ivf.getMax(), val));
                    ivf.setValue(val);
                }
            }
        } catch (NumberFormatException ignored) {
        }
    }


    @FXML
    private void handleDelete(ActionEvent event) {
        try {
            if (onDeleteCallback != null) {
                onDeleteCallback.run();
            }
            Long idToDelete = this.planId;
            java.util.concurrent.CompletableFuture.runAsync(() -> planService.deletePlanById(idToDelete));
        } catch (Exception e) {
            AlertUtils.showError(e.getMessage());
        }
    }
}
