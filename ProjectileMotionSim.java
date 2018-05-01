import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.scene.transform.Rotate;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * JavaFX projectile motion simulator
 * @author Hackerry
 *
 */
public class ProjectileMotionSim extends Application {
    private final int canvasW = 1000, canvasH = 500, circleR = 10, sleep = 20;
    private final double originX = 10, originY = canvasH-originX, g = 9.80665, timeFrame = 0.01, scale = 10;
    private int gridLength = 50;
    private Group gridGroup;
    private Circle ball;
    private double mass, angle, velocity;
    private Thread currThread;
    private final String dataFormat = "%s: %.2f";
    private Label[] data;
    private GraphicsContext gc;
    private boolean drawPath = true;
    private CheckBox showGrid, showPath;
    private Arrow arrow;

    @Override
    public void start(Stage stage) {
        BorderPane root = new BorderPane();
        Pane pane = new Pane();
        HBox control = new HBox(10);
        VBox data = new VBox(10);
        
        initPane(pane);
        initControl(control);
        initData(data);
        
        root.setCenter(pane);
        root.setBottom(control);
        root.setRight(data);
        Scene scene = new Scene(root);
        stage.setOnCloseRequest(e -> {if(currThread != null) currThread.interrupt();});
        stage.setTitle("Projectile Motion Simulator");
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();
    }
    
    private void initPane(Pane pane) {
        Canvas canvas = new Canvas(canvasW, canvasH);
        gc = canvas.getGraphicsContext2D();
        gc.setStroke(Color.RED);
        gc.setLineWidth(3);
        
        ball = new Circle(circleR);
        ball.setCenterX(originX);
        ball.setCenterY(originY);
        ball.setFill(Color.BLACK);
        
        Line X = new Line(originX, originY, originX, 0), Y = new Line(originX, originY, canvasW, originY);
        X.setFill(Color.BLACK);
        Y.setFill(Color.BLACK);
        
        generateGrid(pane, gridLength);
        
        arrow = new Arrow();
        arrow.moveTo(originX, originY);
        arrow.setRotation(60);
        
        pane.getChildren().addAll(canvas, ball, X, Y, arrow);
    }
    
    private void generateGrid(Pane pane, int length) {
        if(gridGroup != null) pane.getChildren().removeAll(gridGroup.getChildren());
        gridGroup = new Group();
        
        double temp;
        Line l;
        for(int i = 1; i < (canvasW-originX)/length; i++) {
            temp = i*length+originX;
            l = new Line(temp, 0, temp, originY);
            l.setFill(Color.LIGHTGRAY);
            l.setStrokeWidth(0.5);
            gridGroup.getChildren().add(l);
        }
        
        for(int i = 1; i < originY/length; i++) {
            temp = originY-i*length;
            l = new Line(originX, temp, canvasW, temp);
            l.setFill(Color.LIGHTGRAY);
            l.setStrokeWidth(0.5);
            gridGroup.getChildren().add(l);
        }
        
        pane.getChildren().addAll(gridGroup);
    }

    private void initControl(HBox control) {
        HBox angleBox = new HBox(5), velocityBox = new HBox(5);
        TextField angleI = new TextField("30"), velocityI = new TextField("30");
        angleI.setPromptText("Enter angle");velocityI.setPromptText("Enter velocity");
        angleI.setMaxWidth(100);velocityI.setMaxWidth(100);
        Label angleL = new Label("Angle: "), velocityL = new Label("Velocity: ");
        angleL.setTextAlignment(TextAlignment.RIGHT);angleL.setFont(Font.font("Times New Roman", FontWeight.BOLD, 20));
        velocityL.setTextAlignment(TextAlignment.RIGHT);velocityL.setFont(Font.font("Times New Roman", FontWeight.BOLD, 20));
        angleBox.getChildren().addAll(angleL, angleI);
        velocityBox.getChildren().addAll(velocityL, velocityI);
        
        angleI.setOnKeyReleased(e -> {
            if(e.isControlDown()) {
                if(e.getCode() == KeyCode.V) e.consume();
            }
            if(angleI.getText().isEmpty()) arrow.setRotation(90);
            double d;
            try {
                d = Double.parseDouble(angleI.getText());
                if(d < 0 || d > 90) return;
                arrow.setRotation(90-d);
            } catch(Exception ex) {return;}
            
        });
        
        Button reset = new Button("Reset"), fire = new Button("Fire"), clear = new Button("Clear");
        fire.setOnAction(e -> {
            try {
                 angle = Double.parseDouble(angleI.getText());
                 velocity = Double.parseDouble(velocityI.getText());
            } catch(Exception ex) {
                reportError();
                return;
            }

            if(angle < 0 || angle > 90) {reportError(); return;}
            fire.setDisable(true);
            showPath.setDisable(true);
            reset.setDisable(false);
            
            System.out.println(mass + " " + angle + " " + velocity);
            currThread = new Thread(simulation);
            currThread.start();
        });
        reset.setOnAction(e -> {
            if(currThread != null) currThread.interrupt();
            ball.setCenterX(originX);
            ball.setCenterY(originY);
            reset.setDisable(true);
            fire.setDisable(false);
            showPath.setDisable(false);
        });
        reset.setDisable(true);
        clear.setOnAction(e -> {
            gc.clearRect(0, 0, canvasW, canvasH);
        });
        
        control.getChildren().addAll(angleBox, velocityBox, fire, reset, clear);
        control.setPadding(new Insets(12));
    }
    
    private Runnable simulation = new Runnable() {
        @Override
        public void run() {
            double time = 0;
            double initX = velocity*Math.cos(angle*Math.PI/180), initY = velocity*Math.sin(angle*Math.PI/180);
            double Ymax = canvasH, tmid = 0;
            if(drawPath) {
                gc.moveTo(originX, originY);
                gc.beginPath();
            }
            do {
                time += timeFrame;
                final double newX = ball.getCenterX()+initX*timeFrame*scale, 
                        newY = ball.getCenterY()-initY*timeFrame*scale+0.5*g*Math.pow(timeFrame, 2)*scale;
                final double Vy = initY -= g*timeFrame, t = time;
                if(newY < Ymax) {Ymax = newY;tmid = time;}
                final double Ytmp = (originY-Ymax)/scale, Ttemp = tmid;
                Platform.runLater(()-> {
                    ball.setCenterX(newX);
                    ball.setCenterY(newY);
                    data[0].setText(String.format(dataFormat, "X", (newX-originX)/scale));
                    data[1].setText(String.format(dataFormat, "Y", (originY-newY)/scale));
                    data[2].setText(String.format(dataFormat, "Vx", initX));
                    data[3].setText(String.format(dataFormat, "Vy", Vy));
                    data[5].setText(String.format(dataFormat, "t", t));
                    data[6].setText(String.format(dataFormat, "Ymax", Ytmp));
                    data[7].setText(String.format(dataFormat, "tmid", Ttemp));
                    if(drawPath) {
                        gc.lineTo(newX, newY);
                        gc.stroke();
                    }
                });
                try {
                    Thread.sleep(sleep);
                } catch(Exception ex) {return;}
            } while(ball.getCenterY() < originY);
            System.out.println("Hit ground");
            ball.setCenterY(originY);
        }
    };
    
    private void initData(VBox box) {
        data = new Label[8];
        data[0] = new Label("X: 0.00");
        data[1] = new Label("Y: 0.00");
        data[2] = new Label("Vx: 0.00");
        data[3] = new Label("Vy: 0.00");
        data[4] = new Label(String.format(dataFormat, "g", g));
        data[5] = new Label("t: 0.00");
        data[6] = new Label("Ymax: 0.00");
        data[7] = new Label("tmid: 0.00");
        
        box.getChildren().addAll(data);
        box.setAlignment(Pos.TOP_LEFT);
        box.setPadding(new Insets(12));
        for(Node n: box.getChildren()) {
            Label l = (Label)n;
            l.setFont(Font.font("Times New Roman", FontWeight.BOLD, 20));
        }
        
        VBox bottomPart = new VBox(10);
        HBox legand = new HBox(5);
        Pane p = new Pane();
        Line a = new Line(0,10,0,15), b = new Line(gridLength,10,gridLength,15), c = new Line(0,15,gridLength,15);
        p.getChildren().addAll(a, b, c);
        Label l = new Label("Length: 5m");
        legand.getChildren().addAll(p, l);
        l.setFont(Font.font("Times New Roman", FontWeight.BOLD, 20));
        box.getChildren().add(legand);
        
        showGrid = new CheckBox("Show Grid");
        showPath = new CheckBox("Show Path");
        showGrid.setSelected(true);
        showGrid.setOnAction(e -> {
            gridGroup.setVisible(!gridGroup.isVisible());
        });
        showPath.setSelected(true);
        showPath.setOnAction(e -> {
            drawPath = !drawPath;
        });
        bottomPart.getChildren().addAll(legand, showGrid, showPath);
        bottomPart.setPadding(new Insets(30,0,0,0));
        box.getChildren().add(bottomPart);
    }
    
    private void reportError() {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Warning");
        
        VBox box = new VBox(10);
        box.setPrefSize(400, 200);
        Label warn = new Label("Invalid Input");
        Label w1 = new Label("Angle should be in range 0-90");
        Label w2 = new Label("No value should be negative");
        box.getChildren().addAll(warn, w1, w2);
        box.setAlignment(Pos.CENTER);
        for(Node n: box.getChildren()) {
            Label l = (Label)n;
            l.setFont(Font.font("Times New Roman", FontWeight.BOLD, 20));
            l.setTextAlignment(TextAlignment.LEFT);
        }
        Button ok = new Button("Ok");
        ok.setOnAction(exx -> stage.close());
        box.getChildren().add(ok);
        Scene scene = new Scene(box);
        stage.setScene(scene);
        stage.show();
    }
    
    private class Arrow extends Group {
        public static final double half = 5.0, line = 30.0;
        private Rotate rotation = null;
        public Arrow() {
            Polygon tri = new Polygon();
            tri.getPoints().addAll(half,0.0,0.0,2*half,2*half,2*half);
            
            Line l = new Line(half,2*half,half,2*half+line);
            this.getChildren().addAll(tri, l);
            
            rotation = new Rotate(0, half, 2*half+line);
            this.getTransforms().add(rotation);
        }
        public void moveTo(double X, double Y) {
            this.setLayoutX(X-half);
            this.setLayoutY(Y-line-2*half);
        }
        
        public void setRotation(double angle) {
            rotation.setAngle(angle);
        }
    }
    
    public static void main(String[] args) {
        Application.launch(args);
    }
}
