package com.ocr.javafx.controller.components;

import com.ocr.javafx.ApplicationContext;
import com.ocr.javafx.entity.LearningPlan;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.util.List;

public class ActivePlansController {

    private ApplicationContext applicationContext;

    @FXML
    public VBox plansContainer;

    public void setApplicationContext(ApplicationContext applicationContext){
        this.applicationContext = applicationContext;
    }

    public void setupActivePlans() {
        Long userId = applicationContext
                .getSessionManager()
                .getCurrentUser()
                .getId();

        List<LearningPlan> plans = applicationContext
                .getLearningPlanService()
                .getActivePlans(userId);
        applyActivePlans(plans);
    }

    public void applyActivePlans(List<LearningPlan> plans) {
        plansContainer.getChildren().clear();
        if (plans == null) {
            return;
        }
        for (LearningPlan plan : plans) {
            try {
                FXMLLoader loader = new FXMLLoader(
                        getClass().getResource("/com/ocr/javafx/components/active-plan-card.fxml")
                );

                AnchorPane card = loader.load();

                ActivePlansCardController controller = loader.getController();
                controller.setData(plan);

                plansContainer.getChildren().add(card);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
