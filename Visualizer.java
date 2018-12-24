import java.io.File;
import java.io.PrintWriter;
import java.util.LinkedList;
import java.util.Scanner;
import java.util.Stack;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class Visualizer extends Application {
    private static final int WIDTH = 800, HEIGHT = 800, CONTROL_WIDTH = 200;
    private static final Color WALL = Color.BLACK, VISITED = Color.DARKSEAGREEN, EMPTY = Color.WHITE,
            START = Color.RED, END = Color.BLUEVIOLET, ONPATH = Color.YELLOW;
    private static final Font textFont = Font.font("Times New Roman", 20);

    private Canvas canvas;
    private GraphicsContext gc;
    private Node[][] maze;
    private int size, col, row;
    private boolean pressed = false;
    private LinkedList<Node> selectNodes = new LinkedList<>();

    private Node start, end;
    private boolean selectingS = false, selectingE = false;
    private Button selectStart, selectEnd;
    
    private Thread simulation;
    private Button[] buttonGroup;

    @Override
    public void start(Stage stage) throws Exception {
        BorderPane root = new BorderPane();

        canvas = new Canvas(WIDTH, HEIGHT);
        gc = canvas.getGraphicsContext2D();
        root.setCenter(canvas);

        GridPane controlBox = new GridPane();
        buttonGroup = new Button[6];

        // Change Column and Row control
        Text rowT = new Text("Row:");
        rowT.setFont(textFont);
        rowT.setTextAlignment(TextAlignment.RIGHT);
        TextField rowI = new TextField();
        rowI.setPrefColumnCount(5);
        rowI.setText("40");
        controlBox.add(rowT, 0, 0);
        controlBox.add(rowI, 1, 0);
        Text colT = new Text("Col:");
        colT.setFont(textFont);
        colT.setTextAlignment(TextAlignment.RIGHT);
        TextField colI = new TextField();
        colI.setText("40");
        colI.setPrefColumnCount(5);
        controlBox.add(colT, 0, 1);
        controlBox.add(colI, 1, 1);
        Text sizeT = new Text("Size:");
        sizeT.setFont(textFont);
        sizeT.setTextAlignment(TextAlignment.RIGHT);
        TextField sizeI = new TextField();
        sizeI.setPrefColumnCount(5);
        sizeI.setText("20");
        controlBox.add(sizeT, 0, 2);
        controlBox.add(sizeI, 1, 2);
        GridPane.setConstraints(rowT, 0, 0, 1, 1, HPos.RIGHT, VPos.CENTER);
        GridPane.setConstraints(colT, 0, 1, 1, 1, HPos.RIGHT, VPos.CENTER);
        GridPane.setConstraints(sizeT, 0, 2, 1, 1, HPos.RIGHT, VPos.CENTER);
        Button resetMaze = new Button("Set Maze");
        resetMaze.setOnAction(e -> {
            try {
                int row = Integer.parseInt(rowI.getText().trim());
                int col = Integer.parseInt(colI.getText().trim());
                int size = -1;
                try {
                    size = Integer.parseInt(sizeI.getText().trim());
                } catch (Exception ex) {
                }

                final int SIZE = size;
                System.out.println(SIZE);
                Platform.runLater(() -> {
                    reset(col, row, SIZE);
                });

            } catch (Exception ex2) {
            }
        });
        controlBox.add(resetMaze, 0, 3, 2, 1);
        resetMaze.setPrefWidth(CONTROL_WIDTH);
        GridPane.setConstraints(resetMaze, 0, 3, 2, 1, HPos.CENTER, VPos.CENTER);

        registerListeners();

        // Select control
        selectStart = new Button("Select Start");
        selectEnd = new Button("Select End");
        selectStart.setOnAction(e -> {
            selectingS = true;
            selectStart.setDisable(true);
            selectEnd.setDisable(true);
        });
        selectEnd.setOnAction(e -> {
            selectingE = true;
            selectStart.setDisable(true);
            selectEnd.setDisable(true);
        });

        controlBox.add(selectStart, 0, 4, 2, 1);
        controlBox.add(selectEnd, 0, 5, 2, 1);
        selectStart.setPrefWidth(CONTROL_WIDTH);
        selectEnd.setPrefWidth(CONTROL_WIDTH);
        buttonGroup[0] = selectStart;
        buttonGroup[1] = selectEnd;
        GridPane.setConstraints(selectStart, 0, 4, 2, 1, HPos.CENTER, VPos.CENTER);
        GridPane.setConstraints(selectEnd, 0, 5, 2, 1, HPos.CENTER, VPos.CENTER);

        // Search type control
        Text alT = new Text("Select Algorithm:");
        alT.setFont(textFont);
        ToggleGroup algorithms = new ToggleGroup();
        RadioButton DFS = new RadioButton("DFS"), BFS = new RadioButton("BFS");
        DFS.setToggleGroup(algorithms);DFS.setFont(textFont);
        BFS.setToggleGroup(algorithms);BFS.setFont(textFont);
        Button start = new Button("Start Pathfinding!"), reset = new Button("Reset");
        start.setOnAction(e -> {
            clearVisitedAndPath();
            disableButtonsAndCanvas();
            if(simulation != null && simulation.isAlive()) simulation.interrupt();
            
            switch((String)algorithms.getSelectedToggle().getUserData()) {
                case "DFS":
                    DFSSimulation();
                    break;
                case "BFS":
                    BFSSimulation();
                    break;
            }
        });
        reset.setOnAction(e -> {
            clearVisitedAndPath();
        });
        buttonGroup[2] = start;
        buttonGroup[3] = reset;
        controlBox.add(start, 0, 8);GridPane.setConstraints(start, 0, 8, 2, 1, HPos.CENTER, VPos.CENTER);start.setPrefWidth(CONTROL_WIDTH);
        controlBox.add(reset, 0, 9);GridPane.setConstraints(reset, 0, 9, 2, 1, HPos.CENTER, VPos.CENTER);reset.setPrefWidth(CONTROL_WIDTH);
        controlBox.add(DFS, 0, 6);GridPane.setConstraints(DFS, 0, 6, 2, 1, HPos.LEFT, VPos.CENTER);DFS.setPadding(new Insets(0,0,0,20));
        controlBox.add(BFS, 0, 7);GridPane.setConstraints(BFS, 0, 7, 2, 1, HPos.LEFT, VPos.CENTER);BFS.setPadding(new Insets(0,0,0,20));
        DFS.setUserData("DFS");BFS.setUserData("BFS");
        DFS.setSelected(true);
        
        //Save-Load file settings
        Button saveFile = new Button("Save File"), loadFile = new Button("Load File");
        buttonGroup[4] = saveFile;
        buttonGroup[5] = loadFile;
        saveFile.setOnAction(e -> {
            saveFile();
        });
        loadFile.setOnAction(e -> {
            loadFile();
        });
        controlBox.add(saveFile, 0, 10);GridPane.setConstraints(saveFile, 0, 10, 2, 1, HPos.CENTER, VPos.CENTER);saveFile.setPrefWidth(CONTROL_WIDTH);
        controlBox.add(loadFile, 0, 11);GridPane.setConstraints(loadFile, 0, 11, 2, 1, HPos.CENTER, VPos.CENTER);loadFile.setPrefWidth(CONTROL_WIDTH);
        
        // Maze Panel settings
        controlBox.setHgap(5);
        controlBox.setVgap(5);
        controlBox.setPadding(new Insets(10));
        controlBox.setAlignment(Pos.CENTER);
        root.setLeft(controlBox);

        Scene scene = new Scene(root);
        stage.setScene(scene);
        stage.setTitle("Maze Visualizer");
        stage.show();
    }

    public void reset(int col, int row, int size) {
        if (col <= 0 || row <= 0)
            return;
        
        start = null;
        end = null;

        maze = new Node[row][col];
        for (int i = 0; i < row; i++) {
            for (int j = 0; j < col; j++) {
                maze[i][j] = new Node(i, j);
            }
        }

        this.row = row;
        this.col = col;
        redrawPanel(row, col, size);
    }

    private void redrawPanel(int row, int col, int size) {
        selectingS = false;
        selectingE = false;
        reableButtonsAndCanvas();
        if(simulation != null && simulation.isAlive()) simulation.interrupt();
        
        gc.clearRect(0, 0, WIDTH, HEIGHT);

        int width = (int) (WIDTH / col);
        int height = (int) (HEIGHT / row);
        size = size <= 0 ? Math.min(width, height) : size;
        this.size = size;
        gc.setFill(Color.WHITE);
        for (int i = 0; i < row; i++) {
            for (int j = 0; j < col; j++) {
                redrawNode(maze[i][j]);
            }
        }

        gc.setStroke(Color.BLACK);
        for (int i = 0; i <= row; i++) {
            gc.strokeLine(0, i * size, col * size, i * size);
        }
        for (int j = 0; j <= col; j++) {
            gc.strokeLine(j * size, 0, j * size, row * size);
        }
    }

    private void redrawNode(Node node) {
        Platform.runLater(() -> {
            if (node.isWall())
                gc.setFill(WALL);
            else {
                if (node.isStart())
                    gc.setFill(START);
                else if (node.isEnd())
                    gc.setFill(END);
                else if(node.isOnPath())
                    gc.setFill(ONPATH);
                else if (node.isVisited())
                    gc.setFill(VISITED);
                else
                    gc.setFill(EMPTY);
            }

            gc.fillRect(node.getCol() * size + 1, node.getRow() * size + 1, size - 2, size - 2);
        });
    }
    
    private void showPath() {
        Node curr = end.getPrev();
        
        while(curr != null && curr != start) {
            curr.setOnPath(true);
            //System.out.print(curr + "->");
            redrawNode(curr);
            curr = curr.getPrev();
        }
    }
    
    private void clearVisitedAndPath() {
        for(Node[] ns: maze) {
            for(Node n: ns) {
                if(!n.isWall()) {
                    n.setVisited(false);
                    n.setOnPath(false);
                    n.setPrev(null);
                    redrawNode(n);
                }
            }
        }
    }

    private void registerListeners() {
        canvas.setOnMouseClicked(e -> {
            if (!pressed) {
                Node currNode = maze[(int) e.getY() / size][(int) e.getX() / size];
                if (selectingS) {
                    if (currNode == end) {
                        // Empty
                    } else {
                        if (start != null) {System.out.println("Not null");
                            start.setStart(false);
                            redrawNode(start);
                        }
                        start = currNode;
                        start.setStart(true);
                        start.setWall(false);
                    }

                    selectStart.setDisable(false);
                    selectEnd.setDisable(false);
                    selectingS = false;
                } else if (selectingE) {
                    if (currNode == start) {
                        // Empty
                    } else {
                        if (end != null) {
                            end.setEnd(false);
                            redrawNode(end);
                        }
                        end = currNode;
                        end.setEnd(true);
                        end.setWall(false);
                    }
                    selectStart.setDisable(false);
                    selectEnd.setDisable(false);
                    selectingE = false;
                } else {
                    if(currNode.isStart() || currNode.isEnd()) {
                        currNode.setWall(false);
                        return;
                    }
                    currNode.toggleWall();
                }

                redrawNode(currNode);
            }
        });

        canvas.setOnMousePressed(e -> {
            selectNodes.clear();
            pressed = false;
        });

        canvas.setOnMouseDragged(e -> {
            pressed = true;
            if(e.getX() < 0 || e.getX() > WIDTH || e.getY() < 0 || e.getY() > HEIGHT) return;
            double x = e.getX() / size, y = e.getY() / size;

            if (e.getButton() == MouseButton.PRIMARY) {
                Node currNode = maze[(int) y][(int) x];
                if (!selectNodes.contains(currNode) && currNode != start && currNode != end) {
                    currNode.setWall(true);
                    // System.out.println("Move over " + x + " " + y);
                    selectNodes.add(currNode);
                    redrawNode(currNode);
                }
            } else if (e.getButton() == MouseButton.SECONDARY) {
                Node currNode = maze[(int) y][(int) x];
                if (!selectNodes.contains(currNode) && currNode != start && currNode != end) {
                    currNode.setWall(false);
                    // System.out.println("Move over " + x + " " + y);
                    selectNodes.add(currNode);
                    redrawNode(currNode);
                }
            }
        });
    }

    private void DFSSimulation() {
        simulation = new Thread() {
            public void run() {
                if(start == null || end == null) {
                    randomStartEnd();
                }
                Stack<Node> stack = new Stack<>();
                stack.push(start);
                LinkedList<Node> neighbours;
                Node curr = null;
                
                while(!stack.isEmpty()) {
                    curr = stack.pop();
                    
                    if(curr == end) {
                        System.out.println("Path found!");
                        showPath();
                        reableButtonsAndCanvas();
                        return;
                    }
                    
                    curr.setVisited(true);
                    redrawNode(curr);
                    
                    neighbours = getNeighbours(curr);
                    for(Node n: neighbours) {
                        n.setPrev(curr);
                        stack.push(n);
                    }
                    
                    //System.out.println(stack);
                    try {
                        Thread.sleep(50);
                    } catch(Exception ex) {return;}
                }
                
                System.out.println("Unreachable");
                reableButtonsAndCanvas();
            }
        };
        simulation.start();
    }
    
    private void BFSSimulation() {
        simulation = new Thread() {
            public void run() {
                if(start == null || end == null) {
                    randomStartEnd();
                }
                LinkedList<Node> queue = new LinkedList<>();
                queue.add(start);
                LinkedList<Node> neighbours;
                Node curr = null;
                
                while(!queue.isEmpty()) {
                    curr = queue.removeFirst();
                    
                    if(curr == end) {
                        System.out.println("Path found!");
                        showPath();
                        reableButtonsAndCanvas();
                        return;
                    }
                    
                    curr.setVisited(true);
                    redrawNode(curr);
                    
                    neighbours = getNeighbours(curr);
                    for(Node n: neighbours) {
                        n.setPrev(curr);
                        queue.add(n);
                    }
                    
                    //System.out.println(stack);
                    try {
                        Thread.sleep(20);
                    } catch(Exception ex) {return;}
                }
                
                System.out.println("Unreachable");
                reableButtonsAndCanvas();
            }
        };
        simulation.start();
    }
    
    private LinkedList<Node> getNeighbours(Node n) {
        LinkedList<Node> result = new LinkedList<>();
        int x = n.getCol(), y = n.getRow();
        if(y-1 >= 0 && !maze[y-1][x].isWall() &&  !maze[y-1][x].isVisited() && maze[y-1][x].getPrev() == null) result.add(maze[y-1][x]);
        if(x+1 < col && !maze[y][x+1].isWall() &&  !maze[y][x+1].isVisited() && maze[y][x+1].getPrev() == null) result.add(maze[y][x+1]);
        if(y+1 < row && !maze[y+1][x].isWall() &&  !maze[y+1][x].isVisited() && maze[y+1][x].getPrev() == null) result.add(maze[y+1][x]);
        if(x-1 >= 0 && !maze[y][x-1].isWall() &&  !maze[y][x-1].isVisited() && maze[y][x-1].getPrev() == null) result.add(maze[y][x-1]);
        
        return result;
    }
    
    private void disableButtonsAndCanvas() {
        for(Button b: buttonGroup) {
            b.setDisable(true);
        }
        canvas.setOnMouseClicked(null);
        canvas.setOnMousePressed(null);
        canvas.setOnMouseReleased(null);
        canvas.setOnMouseDragged(null);
    }
    
    private void reableButtonsAndCanvas() {
        for(Button b: buttonGroup) {
            b.setDisable(false);
        }
        registerListeners();
    }
    
    private void randomStartEnd() {
        do {
            start = maze[(int)(Math.random()*row)][(int)(Math.random()*col)];
            end = maze[(int)(Math.random()*row)][(int)(Math.random()*col)];
        } while(start.isWall() || end.isWall() || start == end);
        
        start.setStart(true);
        end.setEnd(true);
        redrawNode(start);
        redrawNode(end);
    }
    
    private void saveFile() {
        Stage stage = new Stage();
        
        VBox vBox = new VBox(30);
        Label label = new Label("Enter file name");
        label.setFont(textFont);
        
        TextField input = new TextField();
        input.setFont(textFont);
        
        Button okButton = new Button("OK");
        okButton.setOnAction(e -> {
            String fileName = input.getText().trim();
            if(fileName.equals("") || !Character.isAlphabetic(fileName.charAt(0))) return;
            
            File file = new File(input.getText().trim() + ".mz");
            if(!file.exists()) {
                try {file.createNewFile();}catch(Exception ex) {}
            }
            
            PrintWriter pw = null;
            try {
                pw = new PrintWriter(file);
                pw.println(maze.length + " " + maze[0].length + " " + size);
                for(int i = 0; i < maze.length; i++) {
                    for(int j = 0; j < maze[i].length; j++) {
                        pw.print(getCode(maze[i][j]));
                    }
                    pw.println();
                }
                
                pw.flush();
            } catch(Exception ex) {}
            finally {if(pw != null) pw.close();}
            
            stage.close();
        });
        
        vBox.getChildren().addAll(label, input, okButton);
        vBox.setAlignment(Pos.CENTER);
        vBox.setPadding(new Insets(10));
        
        Scene scene = new Scene(vBox, 400, 200);
        stage.setScene(scene);
        stage.setTitle("Save File");
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.showAndWait();
    }
    
    private void loadFile() {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Maze File");
        fileChooser.getExtensionFilters().addAll(
                new ExtensionFilter("Maze Files", "*.mz"));
        fileChooser.setInitialDirectory(new File(Visualizer.class.getProtectionDomain().getCodeSource().getLocation().getPath()));
        File selectedFile = fileChooser.showOpenDialog(stage);
        if (selectedFile != null) {
           readFile(selectedFile);
           stage.close();
        }
    }
    
    private void readFile(File file) {
        Scanner sc = null;
        int row = 0, col = 0, size = 0;
        try {
            sc = new Scanner(file);
            
            row = Integer.parseInt(sc.next());
            col = Integer.parseInt(sc.next());
            size = Integer.parseInt(sc.next());
            
            maze = new Node[row][col];
            
            String temp;
            String[] cells;
            int j = 0;
            while((temp = sc.next()) != null) {
                cells = temp.split("");
                for(int i = 0; i < maze[0].length; i++) {
                    maze[j][i] = getNode(cells[i], j, i);
                }
                
                j++;
            }
        } catch(Exception e) {}
        finally {
            if(sc != null) sc.close();
        }

        this.row = row;
        this.col = col;
        this.size = size;
        redrawPanel(row, col, size);
    }
    
    private int getCode(Node n) {
        if(n.isStart()) return 2;
        else if(n.isEnd()) return 3;
        else if(n.isWall()) return 1;
        else if(!n.isWall()) return 0;
        else return -1;
    }
    
    private Node getNode(String i, int row, int col) {
        Node result = new Node(row, col);
        switch(i) {
            case "1":
                result.setWall(true);
                break;
            case "2":
                result.setStart(true);
                start = result;
                break;
            case "3":
                result.setEnd(true);
                end = result;
                break;
        }
        
        return result;
    }
    
    public static void main(String[] args) {
        Application.launch();
    }
}
