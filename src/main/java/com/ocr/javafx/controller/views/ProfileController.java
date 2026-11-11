package com.ocr.javafx.controller.views;

import com.ocr.javafx.ApplicationContext;
import com.ocr.javafx.config.SessionManager;
import com.ocr.javafx.controller.components.ChangePasswordController;
import com.ocr.javafx.controller.login.LoginController;
import com.ocr.javafx.controller.main.MainController;
import com.ocr.javafx.dto.request.ProfileRequest;
import com.ocr.javafx.dto.response.AchievementResponse;
import com.ocr.javafx.dto.response.ProfileResponse;
import com.ocr.javafx.entity.User;
import com.ocr.javafx.service.ProfileService;
import com.ocr.javafx.util.Function.AlertUtils;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.Setter;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public class ProfileController {

    @FXML
    private Button btnChangePassword;

    @FXML
    private Button btnEditProfile;

    @FXML
    private Button btnLogout;

    @FXML
    private ImageView imgAchievementHour;

    @FXML
    private ImageView imgAchievementPlan;

    @FXML
    private ImageView imgAchievementStreak;

    @FXML
    private ImageView imgAva;

    @FXML
    private Label lblEmail;

    @FXML
    private Label lblHours;

    @FXML
    private Label lblPlans;

    @FXML
    private Label lblSessions;

    @FXML
    private Label lblStreak;

    @FXML
    private Label txtDesHour;

    @FXML
    private Label txtDesPlan;

    @FXML
    private TextField txtName;

    @FXML
    private Label txtStreak;

    @FXML
    private Label txtTitleHour;

    @FXML
    private Label txtTitlePlan;

    @FXML
    private Label txtTitleStreak;

    private SessionManager sessionManager;
    private ProfileService profileService;
    private String selectedAvatarPath;
    private ApplicationContext applicationContext;

    @Setter
    private MainController mainController;

    @FXML
    public void init(ApplicationContext applicationContext) {
        this.sessionManager = applicationContext.getSessionManager();
        this.profileService = applicationContext.getProfileService();
        this.applicationContext = applicationContext;
        loadProfile();
    }

    private void loadProfile() {
        User currentUser = sessionManager.getCurrentUser();
        if (currentUser == null) return;

        // Hiển thị ngay thông tin cơ bản có sẵn trong phiên đăng nhập
        txtName.setText(currentUser.getUsername());
        lblEmail.setText(currentUser.getEmail());
        if (currentUser.getAvatarPath() != null && !currentUser.getAvatarPath().isEmpty()) {
            imgAva.setImage(new Image("file:" + currentUser.getAvatarPath()));
        }

        String email = currentUser.getEmail();
        CompletableFuture.supplyAsync(() -> profileService.getProfile(email))
                .thenAccept(res -> {
                    if (res == null) return;
                    Platform.runLater(() -> {
                        txtName.setText(res.getUsername());
                        lblEmail.setText(res.getEmail());
                        lblHours.setText(String.valueOf(res.getTotalHours()));
                        lblPlans.setText(String.valueOf(res.getCompletedPlans()));
                        lblStreak.setText(String.valueOf(res.getCurrentStreak()));
                        lblSessions.setText(String.valueOf(res.getSkillsLearned()));

                        if (res.getAvatarPath() != null && !res.getAvatarPath().isEmpty()) {
                            imgAva.setImage(new Image("file:" + res.getAvatarPath()));
                        }

                        if (res.getAchievements() != null && res.getAchievements().size() >= 3) {
                            renderAchievementUI(res.getAchievements().get(0), txtTitlePlan, txtDesPlan, imgAchievementPlan);
                            renderAchievementUI(res.getAchievements().get(1), txtTitleStreak, txtStreak, imgAchievementStreak);
                            renderAchievementUI(res.getAchievements().get(2), txtTitleHour, txtDesHour, imgAchievementHour);
                        }
                    });
                });
    }

    private void renderAchievementUI(AchievementResponse data, Label titleLabel, Label descLabel, ImageView imgView) {
        if (titleLabel != null) titleLabel.setText(data.getTitle());
        if (descLabel != null) descLabel.setText(data.getDescription());

        String iconFileName = data.getIconType() + "_lv" + data.getLevel() + ".png";
        String resourcePath = "/com/ocr/javafx/image/" + iconFileName;

        try {
            var stream = getClass().getResourceAsStream(resourcePath);
            if (stream != null) {
                imgView.setImage(new Image(stream));
            }
        } catch (Exception e) {
            System.err.println("Error loading image: " + resourcePath);
        }

        imgView.setOpacity(data.isUnlocked() ? 1.0 : 0.4);
    }

    @FXML
    void handleEditProfile(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Choose Avatar");

        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            selectedAvatarPath = file.getAbsolutePath();
            imgAva.setImage(new Image("file:" + selectedAvatarPath));
        }

        ProfileRequest req = new ProfileRequest();
        req.setEmail(sessionManager.getCurrentUser().getEmail());
        req.setUsername(txtName.getText());
        req.setAvatarPath(selectedAvatarPath);

        CompletableFuture.supplyAsync(() -> profileService.updateProfile(req))
                .thenAccept(updated -> {
                    if (updated) {
                        Platform.runLater(() -> {
                            loadProfile();
                            if (mainController != null && mainController.getTopbarController() != null) {
                                mainController.getTopbarController().setUser(sessionManager.getCurrentUser());
                            }
                        });
                    }
                });
    }

    @FXML
    void handleOpenChangePassword(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/ocr/javafx/components/change-password.fxml"));
            Parent root = loader.load();

            ChangePasswordController controller = loader.getController();
            controller.init(this.applicationContext);

            Stage popupStage = new Stage();
            popupStage.setTitle("Đổi mật khẩu - O-Range");

            popupStage.initModality(Modality.APPLICATION_MODAL);
            popupStage.initOwner(((Node) event.getSource()).getScene().getWindow());

            popupStage.setScene(new Scene(root));
            popupStage.setResizable(false);
            popupStage.showAndWait();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    void handleLogout(ActionEvent event) {
        if (applicationContext != null && applicationContext.getSessionManager() != null) {
            applicationContext.getSessionManager().clear();
        }

        try {
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/ocr/javafx/login/login.fxml"));
            Scene scene = new Scene(loader.load());

            LoginController controller = loader.getController();
            controller.setApplicationContext(applicationContext);

            stage.setScene(scene);
        } catch (IOException e) {
            e.printStackTrace();
            AlertUtils.showError("Không thể quay lại màn hình đăng nhập.");
        }
    }

}
