package com.ocr.javafx.controller.views;

import com.ocr.javafx.ApplicationContext;
import com.ocr.javafx.controller.main.MainController;
import com.ocr.javafx.dto.request.LearningPlanRequest;
import com.ocr.javafx.dto.response.LearningPlanResponse;
import com.ocr.javafx.entity.LearningPlan;
import com.ocr.javafx.entity.User;
import com.ocr.javafx.enums.View;
import com.ocr.javafx.repository.LearningPlanRepository;
import com.ocr.javafx.repository.UserRepository;
import com.ocr.javafx.service.LearningPlanService;
import com.ocr.javafx.util.Function.AlertUtils;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.*;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.ResourceBundle;


public class NewLearningPlanController implements Initializable {

    private LearningPlanService planService;
    private ApplicationContext applicationContext;
    private Long currentUserId;
    private UserRepository userRepository;

    @FXML
    private TextField txtTitle, txtDomain;
    @FXML
    private TextArea txtGoal;
    @FXML
    private Label lblDuration;
    @FXML
    private Label lblTargetSessions;
    @FXML
    private Label lblPageHeader;
    @FXML
    private ComboBox<String> comboIntensity;
    @FXML
    private Button btnBack;
    @FXML
    private Button btnCreatePlan;

    private int duration = 14;
    private int targetSessions = 10;
    private Long editingPlanId = null;
    private MainController mainController;

    public void init(ApplicationContext context) {
        this.applicationContext = context;
        this.planService = context.getLearningPlanService();
        this.currentUserId = context.getSessionManager().getCurrentUserId();
        this.userRepository = context.getUserRepository();
        this.editingPlanId = null;
    }

    public void initForEdit(ApplicationContext context, com.ocr.javafx.dto.LearningPlanDTO planDto) {
        init(context);
        if (planDto == null) return;
        this.editingPlanId = planDto.getId();

        if (lblPageHeader != null) {
            lblPageHeader.setText("Edit Learning Plan");
        }
        if (btnCreatePlan != null) {
            btnCreatePlan.setText("Save Changes");
        }

        txtTitle.setText(planDto.getTitle() != null ? planDto.getTitle() : "");
        txtDomain.setText(planDto.getDomain() != null ? planDto.getDomain() : "");
        txtGoal.setText(planDto.getGoal() != null ? planDto.getGoal() : "");

        this.duration = (planDto.getDurationDays() != null && planDto.getDurationDays() > 0)
                ? planDto.getDurationDays()
                : 14;
        this.targetSessions = (planDto.getTargetSessions() != null && planDto.getTargetSessions() > 0)
                ? planDto.getTargetSessions()
                : this.duration;

        lblDuration.setText(String.valueOf(this.duration));
        if (lblTargetSessions != null) {
            lblTargetSessions.setText(String.valueOf(this.targetSessions));
        }
        if (planDto.getIntensity() != null && !planDto.getIntensity().isBlank()) {
            comboIntensity.getSelectionModel().select(planDto.getIntensity());
        }
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        comboIntensity.setItems(FXCollections.observableArrayList("Low", "Medium", "High"));
        comboIntensity.getSelectionModel().select("Medium");
        lblDuration.setText(String.valueOf(duration));
        if (lblTargetSessions != null) {
            lblTargetSessions.setText(String.valueOf(targetSessions));
        }
    }

    public void setMainController(MainController mainController) {
        this.mainController = mainController;
    }

    @FXML
    private void incrementDays() {
        lblDuration.setText(String.valueOf(++duration));
    }

    @FXML
    private void decrementDays() {
        if (duration > 1) {
            lblDuration.setText(String.valueOf(--duration));
        }
    }

    @FXML
    private void incrementSessions() {
        targetSessions++;
        if (lblTargetSessions != null) {
            lblTargetSessions.setText(String.valueOf(targetSessions));
        }
    }

    @FXML
    private void decrementSessions() {
        if (targetSessions > 1) {
            targetSessions--;
            if (lblTargetSessions != null) {
                lblTargetSessions.setText(String.valueOf(targetSessions));
            }
        }
    }

    @FXML
    private void handleCreatePlan() {
        String title = txtTitle.getText() != null ? txtTitle.getText().trim() : "";
        String domain = txtDomain.getText() != null ? txtDomain.getText().trim() : "";
        String goal = txtGoal.getText() != null ? txtGoal.getText().trim() : "";
        String intensity = comboIntensity.getValue();
        int dur = this.duration;
        int target = this.targetSessions;

        if (title.isEmpty() || domain.isEmpty()) {
            AlertUtils.showAlert(Alert.AlertType.WARNING, "Validation Error", "Title and Domain cannot be empty!");
            return;
        }
        if (btnCreatePlan != null) {
            btnCreatePlan.setDisable(true);
        }

        if (editingPlanId != null) {
            Long planId = editingPlanId;
            java.util.concurrent.CompletableFuture.supplyAsync(() ->
                    planService.updateLearningPlan(planId, title, domain, goal, dur, target, intensity)
            ).thenAccept(updated -> javafx.application.Platform.runLater(() -> {
                if (btnCreatePlan != null) {
                    btnCreatePlan.setDisable(false);
                }
                if (updated != null) {
                    AlertUtils.showAlert(Alert.AlertType.INFORMATION, "Success", "Đã cập nhật mục tiêu kế hoạch học tập thành công!");
                    handleBack();
                } else {
                    AlertUtils.showAlert(Alert.AlertType.ERROR, "Error", "Không thể cập nhật kế hoạch học tập!");
                }
            }));
            return;
        }

        LearningPlanRequest request = new LearningPlanRequest(
                title,
                goal,
                intensity,
                new ArrayList<>(),
                domain,
                dur,
                target
        );
        java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            User currentUser = applicationContext.getSessionManager().getCurrentUser();
            if (currentUser == null) {
                currentUser = userRepository.findById(currentUserId);
            }
            return planService.createLearningPlan(currentUser, request);
        }).thenAccept(response -> javafx.application.Platform.runLater(() -> {
            if (btnCreatePlan != null) {
                btnCreatePlan.setDisable(false);
            }
            if (response.isSuccess()) {
                AlertUtils.showAlert(Alert.AlertType.INFORMATION, "Success", "Learning Plan created successfully!");
                handleBack();
            } else {
                AlertUtils.showAlert(Alert.AlertType.ERROR, "Error", response.getResponse());
            }
        }));
    }

    @FXML
    private void handleBack() {
        if (mainController != null) {
            mainController.setContent("/com/ocr/javafx/views/learning-plan-view.fxml", View.LEARNING_PLANS);
        } else {
            System.err.println("MainController is null! Không thể quay lại.");
        }
    }
}