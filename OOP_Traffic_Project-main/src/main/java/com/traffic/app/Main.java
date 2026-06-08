package com.traffic.app;

import com.traffic.config.Constants;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import com.traffic.view.MainView;

public class Main extends Application {
    
    @Override
    public void start(Stage primaryStage) {
        MainView mainView = new MainView();
        Scene scene = new Scene(mainView.getRoot(), Constants.WINDOW_WIDTH, Constants.WINDOW_HEIGHT);
        
        primaryStage.setTitle("Smart Urban Traffic Simulation (JavaFX Engine)");
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.show();
        
        // Bắt đầu vòng lặp game
        mainView.startSimulation();
    }

    public static void main(String[] args) {
        launch(args);
    }
}