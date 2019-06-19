import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.PriorityQueue;
import java.util.Stack;
import java.util.logging.Logger;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Control;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

public class RouteSimulators extends Application {
	private static final int WIDTH = 1000, HEIGHT = 600, CONTROL_WIDTH = 200, SIDE_WIDTH = 200,
			MAIN_WIDTH = WIDTH - CONTROL_WIDTH - SIDE_WIDTH, NORMAL_R = 3, BIG_R = 5, PADDING = 40, ICON_R = 7,
			ANIME_DURATION = 50, SLEEP_DURATION = 100, PROBE_DURATION = 50, SHOW_PATH_DURATION = 200;
	private static final int ASTAR = 0, DIJKSTRA = 1, GREEDY = 2, BFS = 3, DFS = 4;
	private static final double PROBE_PATH_W = 3, SOLID_PATH_W = 3.5, EDGE_W = 1, EDGE_DASH_AMT = 5.0;
	private static final Color EDGE_COLOR = Color.BLACK, PROB_COLOR = Color.DARKOLIVEGREEN,
			PATH_COLOR = Color.INDIANRED;
	private static int A_STAR_CONSTANT = 100;
	private ArrayList<Point> points = new ArrayList<>();
	private ArrayList<Control> controls = new ArrayList<>();
	private double[][] matrix;
	private Pane mainPanel;
	private TextArea sidePanel;
	private static final Logger log = Logger.getLogger(RouteSimulators.class.getName());
	private Point startPoint = null, endPoint = null;
	private boolean isChoosingStart = false, isChoosingEnd = false;
	private Thread simulation;
	private ArrayList<Line> pathLines = new ArrayList<>(), edgeList = new ArrayList<>(), probeLines = new ArrayList<>();
	private boolean animationOn = true;
	private boolean customizeEdge = false;
	private boolean showProbe = true;
	private Button openFileButton;
	private String additionalArg;

	@Override
	public void start(Stage stage) {
		BorderPane root = new BorderPane();

		layoutControlPanel(root);
		layoutSidePanel(root);

		// Main panel stuff
		mainPanel = new Pane();
		mainPanel.setPrefSize(WIDTH - CONTROL_WIDTH - SIDE_WIDTH, HEIGHT);
		root.setCenter(mainPanel);

		root.setPrefSize(WIDTH, HEIGHT);
		Scene scene = new Scene(root);
		stage.setTitle("Route Algorithm Simulator");
		stage.setScene(scene);
		stage.setOnCloseRequest(e -> {
			if (simulation != null && simulation.isAlive()) {
				simulation.interrupt();
			}
		});
		stage.show();
	}

	/**
	 * Handle process file event (click button on read file).
	 * 
	 * @param file        file to read in
	 * @param messageText the message box to output result
	 * @param infoText    the area to display output point
	 */
	private void processFile(File file, Text messageText, Text infoText) {
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new FileReader(file));

			String line;
			String[] parts;
			int lineNum = 0;
			points.clear();
			double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = Double.MIN_VALUE, maxY = Double.MIN_VALUE;
			double x, y;
			while ((line = reader.readLine()) != null) {
				parts = line.split("\\t");
				// System.out.println(Arrays.toString(parts));

				// There are edge entries at the end
				if (line.equals("")) {
					customizeEdge = true;
					break;
				}

				// Else there is a problem
				if (parts.length != 3)
					throw new Exception("3 input expected on line " + lineNum);

				x = Double.parseDouble(parts[1]);
				y = Double.parseDouble(parts[2]);

				if (x < minX)
					minX = x;
				if (x > maxX)
					maxX = x;
				if (y < minY)
					minY = y;
				if (y > maxY)
					maxY = y;
				points.add(new Point(x, y, parts[0], lineNum, infoText));

				lineNum++;
			}

			// Successful read
			Platform.runLater(() -> {
				messageText.setText(file.getName());
			});
			log.info("Successful read with " + points.size() + " points");

			// Initialize matrix
			matrix = new double[points.size()][points.size()];

			// Draw all points
			drawPoints(minX, minY, maxX, maxY);

			// Read customize edge
			double dist;
			if (customizeEdge) {
				lineNum++;
				while ((line = reader.readLine()) != null) {
					parts = line.split("\\t");

					// Add edge list
					int point1 = Integer.parseInt(parts[0]);
					int point2 = Integer.parseInt(parts[1]);

					if (point1 < 0 || point1 >= points.size() || point2 < 0 || point2 >= points.size()) {
						throw new Exception("Invalid index on line: " + lineNum);
					}
					if (point1 == point2) {
						throw new Exception("No self loop on line: " + lineNum);
					}

					// Initialize matrix with undirected edges
					dist = euclideanDist(points.get(point1).x, points.get(point1).y, points.get(point2).x,
							points.get(point2).x);
					matrix[point1][point2] = dist;
					matrix[point2][point1] = dist;

					lineNum++;
				}

				// Make all other edges to be infinity
				for (int i = 0; i < matrix.length; i++) {
					for (int j = 0; j < matrix.length; j++) {
						if (matrix[i][j] == 0)
							matrix[i][j] = Double.MAX_VALUE;
					}
				}

				log.info("Customize edge finished");
			} else {
				calculateDefaultEdge();
			}

			// Draw all the edges if customized, else do nothing
			if (customizeEdge) {
				for (int i = 0; i < matrix.length; i++) {
					for (int j = i + 1; j < matrix.length; j++) {
						if (matrix[i][j] != Double.MAX_VALUE) {
							Point point1 = points.get(i);
							Point point2 = points.get(j);
							Platform.runLater(() -> {
								Line l = new Line(point1.getTranslateX(), point1.getTranslateY(),
										point2.getTranslateX(), point2.getTranslateY());
								l.setStroke(EDGE_COLOR);
								l.setStrokeWidth(EDGE_W);
								l.getStrokeDashArray().addAll(EDGE_DASH_AMT, EDGE_DASH_AMT);
								l.setStrokeDashOffset(10);
								edgeList.add(l);
							});
						}
					}
				}
			}
			Platform.runLater(() -> {
				mainPanel.getChildren().addAll(edgeList);
				// Make points being able to be selected
				for (Line l : edgeList)
					l.toBack();
			});

			// Set control to not disabled
			setControlDisable(false);
		} catch (Exception ex) {
			messageText.setText(ex.getMessage());
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (Exception e) {
				}
			}
			System.out.println();
		}
	}

	/**
	 * Helper method for draw points with given coordinates, fit to the given area.
	 * 
	 * @param minX smallest x
	 * @param minY smallest y
	 * @param maxX biggest x
	 * @param maxY biggest y
	 */
	private void drawPoints(double minX, double minY, double maxX, double maxY) {
		double scaleX = (MAIN_WIDTH - 2 * PADDING) / (maxX - minX);
		double scaleY = (HEIGHT - 2 * PADDING) / (maxY - minY);
		for (Point p : points) {
			Platform.runLater(() -> {
				p.setTranslateX((p.x - minX) * scaleX + PADDING);
				p.setTranslateY((p.y - minY) * scaleY + PADDING);
				mainPanel.getChildren().add(p);
			});
		}

		log.info("Finished draw points " + points.size());
	}

	/**
	 * Calculate paths that interconnect all points.
	 */
	private void calculateDefaultEdge() {
		matrix = new double[points.size()][points.size()];

		double dist;
		for (int i = 0; i < points.size(); i++) {
			// No self-loop allowed
			matrix[i][i] = Double.MAX_VALUE;
			for (int j = i + 1; j < points.size(); j++) {
				// Initialize distance
				dist = euclideanDist(points.get(i).x, points.get(i).y, points.get(j).x, points.get(j).y);
				matrix[i][j] = dist;
				matrix[j][i] = dist;
			}
		}

		log.info("Default path finished");
	}

	/**
	 * Helper method to calculate the shortest path between two points.
	 */
	private double euclideanDist(double x1, double y1, double x2, double y2) {
		return Math.pow(Math.pow(x1 - x2, 2) + Math.pow(y1 - y2, 2), 0.5);
	}

	/**
	 * Layout all control panel components.
	 * 
	 * @param root the root panel.
	 */
	private void layoutControlPanel(BorderPane root) {
		// Control panel stuff
		VBox controlPanel = new VBox(10);
		controlPanel.setPrefSize(CONTROL_WIDTH, HEIGHT);
		RadioButton AStarButton = new RadioButton("A*"), dijkstraButton = new RadioButton("Dijkstra"),
				greedyButton = new RadioButton("Greedy"), BFSButton = new RadioButton("BFS"),
				DFSButton = new RadioButton("DFS");
		AStarButton.setUserData(ASTAR);
		dijkstraButton.setUserData(DIJKSTRA);
		greedyButton.setUserData(GREEDY);
		BFSButton.setUserData(BFS);
		DFSButton.setUserData(DFS);
		ToggleGroup buttonGroup = new ToggleGroup();
		greedyButton.setToggleGroup(buttonGroup);
		dijkstraButton.setToggleGroup(buttonGroup);
		AStarButton.setToggleGroup(buttonGroup);
		BFSButton.setToggleGroup(buttonGroup);
		DFSButton.setToggleGroup(buttonGroup);
		greedyButton.setSelected(true);
		controlPanel.setPadding(new Insets(10));
		controlPanel.getChildren().addAll(greedyButton, dijkstraButton, AStarButton, BFSButton, DFSButton);

		Button chooseStartButton = new Button("Choose start");
		Circle startC = new Circle();
		startC.setRadius(ICON_R);
		startC.setFill(Color.DARKRED);
		chooseStartButton.setGraphic(startC);
		Button chooseEndButton = new Button("Choose end");
		Circle endC = new Circle();
		endC.setRadius(ICON_R);
		endC.setFill(Color.LIMEGREEN);
		chooseEndButton.setGraphic(endC);
		chooseStartButton.setPrefWidth(CONTROL_WIDTH);
		chooseEndButton.setPrefWidth(CONTROL_WIDTH);
		controlPanel.getChildren().addAll(chooseStartButton, chooseEndButton);

		CheckBox animationButton = new CheckBox("Animation");
		animationButton.setSelected(true);
		CheckBox showProbeButton = new CheckBox("Show Probe Lines");
		showProbeButton.setSelected(true);
		Button startButton = new Button("Start");
		startButton.setPrefWidth(CONTROL_WIDTH);

		HBox argBox = new HBox(5);
		Text argText = new Text("Add. Arg: ");
		argText.setTextAlignment(TextAlignment.JUSTIFY);
		argText.setFont(Font.font("Times New Roman", 16));
		TextField arg = new TextField();
		arg.setPromptText("Additional argument...");
		argBox.getChildren().addAll(argText, arg);
		controlPanel.getChildren().add(argBox);

		openFileButton = new Button("Open file...");
		Text currFileText = new Text("No File");
		Text infoText = new Text("");
		currFileText.setFont(Font.font("Times New Roman", 16));
		infoText.setFont(Font.font("Times New Roman", 16));
		openFileButton.setPrefWidth(CONTROL_WIDTH);
		currFileText.setWrappingWidth(CONTROL_WIDTH);
		infoText.setWrappingWidth(CONTROL_WIDTH);
		controlPanel.getChildren().addAll(animationButton, showProbeButton, startButton, openFileButton, currFileText,
				infoText);
		controlPanel.setStyle("-fx-border-width: 0 1px 0 0; -fx-border-style: solid;");

		controls.add(chooseStartButton);
		controls.add(chooseEndButton);
		controls.add(startButton);
		controls.add(showProbeButton);
		controls.add(openFileButton);

		chooseStartButton.setDisable(true);
		chooseEndButton.setDisable(true);
		startButton.setDisable(true);

		// Event handling
		openFileButton.setOnAction(e -> {
			Stage stage = new Stage();
			stage.initModality(Modality.APPLICATION_MODAL);

			FileChooser fileChooser = new FileChooser();
			fileChooser.setTitle("Open Point File");
			fileChooser.getExtensionFilters().addAll(new ExtensionFilter("Point Files", "*.tsv"));
			fileChooser.setInitialDirectory(new File(System.getProperty("user.home")));
			File selectedFile = fileChooser.showOpenDialog(stage);
			if (selectedFile != null) {
				resetMainPanel();
				processFile(selectedFile, currFileText, infoText);
			}
		});

		// Choose start and end
		chooseStartButton.setOnAction(e -> {
			setControlDisable(true);
			isChoosingStart = true;
		});
		chooseEndButton.setOnAction(e -> {
			setControlDisable(true);
			isChoosingEnd = true;
		});

		animationButton.setOnAction(e -> {
			animationOn = !animationOn;
		});
		showProbeButton.setOnAction(e -> {
			showProbe = !showProbe;
		});

		startButton.setOnAction(e -> {
			int algorithm = (int) buttonGroup.getSelectedToggle().getUserData();

			if (startPoint == null || endPoint == null || startPoint == endPoint)
				return;

			// Reset possible fields
			ArrayList<Line> removeLines = new ArrayList<>(pathLines);
			removeLines.addAll(probeLines);
			Platform.runLater(() -> {
				mainPanel.getChildren().removeAll(removeLines);
				sidePanel.setText("");
			});
			probeLines.clear();
			pathLines.clear();

			additionalArg = arg.getText().trim();

			setControlDisable(true);
			switch (algorithm) {
			case ASTAR:
				runAStar();
				break;
			case DIJKSTRA:
				runDijkstra();
				break;
			case GREEDY:
				runGreedy();
				break;
			case BFS:
				runBFS();
				break;
			case DFS:
				runDFS();
				break;
			default:
				break;
			}
		});

		root.setLeft(controlPanel);
	}

	/**
	 * Run greedy search algorithm.
	 */
	private void runGreedy() {
		simulation = new Thread(() -> {
			// Reset all points
			for (Point p : points)
				p.isVisited = false;

			Point currPoint = startPoint;
			int index, id;
			double minL;
			double weight = 0;
			int hop = 0;
			int cycle = 0;

			// Main loop
			while (currPoint != endPoint) {
				id = currPoint.id;
				index = -1;
				minL = Double.MAX_VALUE;
				for (int i = 0; i < matrix[id].length; i++) {
					if (!points.get(i).isVisited && matrix[id][i] != Double.MAX_VALUE && matrix[id][i] < minL) {
						minL = matrix[id][i];
						index = i;
					}
				}

				// Unconnected graph
				if (index == -1)
					break;

				currPoint.isVisited = true;
				weight += minL;
				hop++;

				// Add this stop to list
				String label = currPoint.label;
				Platform.runLater(() -> {
					sidePanel.appendText(label + "->\n");
				});

				// Visited next point
				double startX = currPoint.getTranslateX();
				double startY = currPoint.getTranslateY();
				currPoint = points.get(index);
				drawPathTo(startX, startY, currPoint.getTranslateX(), currPoint.getTranslateY(), PATH_COLOR,
						SOLID_PATH_W, false);

				try {
					Thread.sleep(SLEEP_DURATION);
				} catch (Exception e) {
					return;
				}

				cycle++;
			}

			// Unconnected graph
			if (currPoint != endPoint) {
				setControlDisable(false);
				System.out.println("Graph is unconnected as far as this I can tell");
				return;
			} else {
				String label = currPoint.label;
				double totalWeight = weight;
				int totalHop = hop;
				int totalCycle = cycle;
				Platform.runLater(() -> {
					sidePanel.appendText(label + "\n" + "Path total: " + totalWeight + "\nStop: " + totalHop
							+ "\nCycles: " + totalCycle);
				});
				setControlDisable(false);

				log.info("Path found!");
			}
		});

		simulation.start();
	}

	/**
	 * Run A star algorithm.
	 */
	private void runAStar() {
		simulation = new Thread(() -> {
			int constant;
			try {
				constant = Integer.parseInt(additionalArg);

				if (constant < 0 || constant > Integer.MAX_VALUE) {
					throw new Exception("Out of bounds");
				}
			} catch (Exception e) {
				constant = A_STAR_CONSTANT;
			}

			for (Point p : points) {
				p.isVisited = false;
				p.weight = Double.MAX_VALUE;
				p.prev = null;

				// Calculate all node's estimate distance to end point
				p.estimateDist = constant * euclideanDist(p.x, p.y, endPoint.x, endPoint.y);
			}

			Point currPoint = startPoint, neighborPoint, tempPoint;
			PriorityQueue<Point> queue = new PriorityQueue<>(new AStarComparator());
			int id;
			double newWeight;
			int cycle = 0;

			// Initialize starting point
			queue.add(startPoint);
			startPoint.weight = 0;

			// Main loop
			while (!queue.isEmpty()) {
				currPoint = queue.poll();
				id = currPoint.id;

				// Remove current path lines
				ArrayList<Line> removeLines = new ArrayList<>(pathLines);
				Platform.runLater(() -> {
					mainPanel.getChildren().removeAll(removeLines);
				});
				pathLines.clear();

				// Draw current path in consideration
				tempPoint = currPoint;
				while (tempPoint.prev != null) {
					drawPathTo(tempPoint.getTranslateX(), tempPoint.getTranslateY(), tempPoint.prev.getTranslateX(),
							tempPoint.prev.getTranslateY(), PATH_COLOR, SOLID_PATH_W, false);
					tempPoint = tempPoint.prev;
				}
				// To better visualize probe process
				try {
					Thread.sleep(SHOW_PATH_DURATION);
				} catch (Exception ex) {
				}

				// Reach goal
				if (currPoint == endPoint) {
					System.out.println("Found route");
					break;
				}

				// This node is done
				if (currPoint.isVisited)
					continue;

				// Set visited
				currPoint.isVisited = true;

				// Add neighbors to queue if weight can be reduced
				for (int i = 0; i < matrix[id].length; i++) {
					if (!points.get(i).isVisited && matrix[id][i] != Double.MAX_VALUE) {
						neighborPoint = points.get(i);

						newWeight = currPoint.weight + matrix[id][i];
						// Relax neighbor node and add to priority queue
						if (newWeight < neighborPoint.weight) {
							if (showProbe) {
								// Draw probe line if it's the first time being probed
								if (neighborPoint.weight == Double.MAX_VALUE) {
									double endX = neighborPoint.getTranslateX();
									double endY = neighborPoint.getTranslateY();
									drawPathTo(currPoint.getTranslateX(), currPoint.getTranslateY(), endX, endY,
											PROB_COLOR, PROBE_PATH_W, true);
								}
							}

							// Update this vertex and priority queue
							neighborPoint.prev = currPoint;
							neighborPoint.weight = newWeight;
							queue.add(neighborPoint);
						}

						// To better visualize probe process
						try {
							Thread.sleep(PROBE_DURATION);
						} catch (Exception ex) {
						}
					}
				}

				try {
					Thread.sleep(SLEEP_DURATION);
				} catch (Exception e) {
					return;
				}

				cycle++;
			}

			// Unconnected graph
			if (currPoint != endPoint) {
				System.out.println("Graph is unconnected as far as BFS can tell");
				setControlDisable(false);
				return;
			} else {
				double weight = 0;
				int hop = 0;
				Stack<Point> path = new Stack<>();
				while (currPoint.prev != null) {
					path.push(currPoint);
					weight += matrix[currPoint.id][currPoint.prev.id];
					hop++;
					currPoint = currPoint.prev;
				}

				// Add the starting vertex
				path.push(currPoint);

				// Add path in reverse order to panel
				while (path.size() > 1) {
					currPoint = path.pop();
					String label = currPoint.label;
					Platform.runLater(() -> {
						sidePanel.appendText(label + "->\n");
					});
				}

				currPoint = path.pop();
				String label = currPoint.label;
				double totalWeight = weight;
				int totalHop = hop;
				int totalCycle = cycle;
				Platform.runLater(() -> {
					sidePanel.appendText(label + "\n" + "Path total: " + totalWeight + "\nStop: " + totalHop
							+ "\nCycles: " + totalCycle);
				});

				setControlDisable(false);
				log.info("Path found!");
			}
		});

		simulation.start();
	}

	/**
	 * Run Dijkstra algorithm.
	 */
	private void runDijkstra() {
		simulation = new Thread(() -> {
			for (Point p : points) {
				p.isVisited = false;
				p.weight = Double.MAX_VALUE;
				p.prev = null;
			}

			Point currPoint = startPoint, neighborPoint, tempPoint;
			PriorityQueue<Point> queue = new PriorityQueue<>(new DijkstraComparator());
			int id;
			double newWeight;
			int cycle = 0;

			// Initialize starting point
			queue.add(startPoint);
			startPoint.weight = 0;

			// Main loop
			while (!queue.isEmpty()) {
				currPoint = queue.poll();
				id = currPoint.id;

				// Remove current path lines
				ArrayList<Line> removeLines = new ArrayList<>(pathLines);
				Platform.runLater(() -> {
					mainPanel.getChildren().removeAll(removeLines);
				});
				pathLines.clear();

				// Draw current path in consideration
				tempPoint = currPoint;
				while (tempPoint.prev != null) {
					drawPathTo(tempPoint.getTranslateX(), tempPoint.getTranslateY(), tempPoint.prev.getTranslateX(),
							tempPoint.prev.getTranslateY(), PATH_COLOR, SOLID_PATH_W, false);
					tempPoint = tempPoint.prev;
				}
				// To better visualize probe process
				try {
					Thread.sleep(SHOW_PATH_DURATION);
				} catch (Exception ex) {
				}

				// Reach goal
				if (currPoint == endPoint) {
					System.out.println("Found route");
					break;
				}

				// This node is done
				if (currPoint.isVisited)
					continue;

				// Set visited
				currPoint.isVisited = true;

				// Add neighbors to queue if weight can be reduced
				for (int i = 0; i < matrix[id].length; i++) {
					if (!points.get(i).isVisited && matrix[id][i] != Double.MAX_VALUE) {
						neighborPoint = points.get(i);

						newWeight = currPoint.weight + matrix[id][i];
						// Relax neighbor node and add to priority queue
						if (newWeight < neighborPoint.weight) {
							if (showProbe) {
								// Draw probe line if it's the first time being probed
								if (neighborPoint.weight == Double.MAX_VALUE) {
									double endX = neighborPoint.getTranslateX();
									double endY = neighborPoint.getTranslateY();
									drawPathTo(currPoint.getTranslateX(), currPoint.getTranslateY(), endX, endY,
											PROB_COLOR, PROBE_PATH_W, true);
								}
							}

							// Update this vertex and priority queue
							neighborPoint.prev = currPoint;
							neighborPoint.weight = newWeight;
							queue.add(neighborPoint);
						}

						// To better visualize probe process
						try {
							Thread.sleep(PROBE_DURATION);
						} catch (Exception ex) {
						}
					}
				}

				try {
					Thread.sleep(SLEEP_DURATION);
				} catch (Exception e) {
					return;
				}

				cycle++;
			}

			// Unconnected graph
			if (currPoint != endPoint) {
				System.out.println("Graph is unconnected as far as BFS can tell");
				setControlDisable(false);
				return;
			} else {
				double weight = 0;
				int hop = 0;
				Stack<Point> path = new Stack<>();
				while (currPoint.prev != null) {
					path.push(currPoint);
					weight += matrix[currPoint.id][currPoint.prev.id];
					hop++;
					currPoint = currPoint.prev;
				}

				// Add the starting vertex
				path.push(currPoint);

				// Add path in reverse order to panel
				while (path.size() > 1) {
					currPoint = path.pop();
					String label = currPoint.label;
					Platform.runLater(() -> {
						sidePanel.appendText(label + "->\n");
					});
				}

				currPoint = path.pop();
				String label = currPoint.label;
				double totalWeight = weight;
				int totalHop = hop;
				int totalCycle = cycle;
				Platform.runLater(() -> {
					sidePanel.appendText(label + "\n" + "Path total: " + totalWeight + "\nStop: " + totalHop
							+ "\nCycles: " + totalCycle);
				});

				setControlDisable(false);
				log.info("Path found!");
			}
		});

		simulation.start();
	}

	/**
	 * Run BFS.
	 */
	private void runBFS() {
		simulation = new Thread(() -> {
			// Reset all points
			for (Point p : points) {
				p.isVisited = false;
				p.prev = null;
			}

			Point currPoint = startPoint, neighborPoint;
			int id;
			int cycle = 0;
			LinkedList<Point> queue = new LinkedList<>();

			// Enqueue first node
			queue.add(startPoint);
			startPoint.isVisited = true;

			// Main loop
			while (!queue.isEmpty()) {
				currPoint = queue.removeFirst();
				id = currPoint.id;

				// Reach goal
				if (currPoint == endPoint) {
					System.out.println("Found route");
					break;
				}

				// Add all unvisited neighbors to queue
				for (int i = 0; i < matrix[id].length; i++) {
					if (!points.get(i).isVisited && matrix[id][i] != Double.MAX_VALUE) {
						neighborPoint = points.get(i);
						queue.addLast(neighborPoint);

						// To ensure every node is enqueued only once
						neighborPoint.isVisited = true;
						neighborPoint.prev = currPoint;

						// Draw all lines to the neighbors
						double endX = neighborPoint.getTranslateX();
						double endY = neighborPoint.getTranslateY();
						drawPathTo(currPoint.getTranslateX(), currPoint.getTranslateY(), endX, endY, PROB_COLOR,
								PROBE_PATH_W, true);

						// To better visualize probe process
						try {
							Thread.sleep(PROBE_DURATION);
						} catch (Exception ex) {
						}

					}
				}

				try {
					Thread.sleep(SLEEP_DURATION);
				} catch (Exception e) {
					return;
				}

				cycle++;
			}

			// Unconnected graph
			if (currPoint != endPoint) {
				System.out.println("Graph is unconnected as far as BFS can tell");
				setControlDisable(false);
				return;
			} else {
				double weight = 0;
				int hop = 0;
				Stack<Point> path = new Stack<>();
				while (currPoint.prev != null) {
					path.push(currPoint);
					weight += matrix[currPoint.id][currPoint.prev.id];
					hop++;
					currPoint = currPoint.prev;
				}

				// Add the starting vertex
				path.push(currPoint);

				// Add path in reverse order to panel
				while (path.size() > 1) {
					currPoint = path.pop();
					String label = currPoint.label;
					Platform.runLater(() -> {
						sidePanel.appendText(label + "->\n");
					});

					drawPathTo(currPoint.getTranslateX(), currPoint.getTranslateY(), path.peek().getTranslateX(),
							path.peek().getTranslateY(), PATH_COLOR, SOLID_PATH_W, false);
				}

				currPoint = path.pop();
				String label = currPoint.label;
				double totalWeight = weight;
				int totalHop = hop;
				int totalCycle = cycle;
				Platform.runLater(() -> {
					sidePanel.appendText(label + "\n" + "Path total: " + totalWeight + "\nStop: " + totalHop
							+ "\nCycles: " + totalCycle);
				});

				setControlDisable(false);
				log.info("Path found!");
			}
		});

		simulation.start();
	}

	/**
	 * Run DFS.
	 */
	private void runDFS() {
		simulation = new Thread(() -> {
			// Reset all points
			for (Point p : points) {
				p.isVisited = false;
				p.prev = null;
			}

			Point currPoint = startPoint, neighborPoint;
			int id;
			Stack<Point> stack = new Stack<>();
			int cycle = 0;

			// Enqueue first node
			stack.push(startPoint);
			startPoint.isVisited = true;

			// Main loop
			while (!stack.isEmpty()) {
				currPoint = stack.pop();
				id = currPoint.id;

				// Reach goal
				if (currPoint == endPoint) {
					System.out.println("Found route");
					break;
				}

				// Add all unvisited neighbors to queue
				for (int i = 0; i < matrix[id].length; i++) {
					if (!points.get(i).isVisited && matrix[id][i] != Double.MAX_VALUE) {
						neighborPoint = points.get(i);
						stack.push(neighborPoint);

						// To ensure every node is enqueued only once
						neighborPoint.isVisited = true;
						neighborPoint.prev = currPoint;

						// Draw all lines to the neighbors
						double endX = neighborPoint.getTranslateX();
						double endY = neighborPoint.getTranslateY();
						drawPathTo(currPoint.getTranslateX(), currPoint.getTranslateY(), endX, endY, PROB_COLOR,
								PROBE_PATH_W, true);

						// To better visualize probe process
						try {
							Thread.sleep(PROBE_DURATION);
						} catch (Exception ex) {
						}
					}
				}

				try {
					Thread.sleep(SLEEP_DURATION);
				} catch (Exception e) {
					return;
				}

				cycle++;
			}

			// Unconnected graph
			if (currPoint != endPoint) {
				System.out.println("Graph is unconnected as far as BFS can tell");
				setControlDisable(false);
				return;
			} else {
				double weight = 0;
				int hop = 0;
				Stack<Point> path = new Stack<>();
				while (currPoint.prev != null) {
					path.push(currPoint);
					weight += matrix[currPoint.id][currPoint.prev.id];
					hop++;
					currPoint = currPoint.prev;
				}

				// Add the starting vertex
				path.push(currPoint);

				// Add path in reverse order to panel
				while (path.size() > 1) {
					currPoint = path.pop();
					String label = currPoint.label;
					Platform.runLater(() -> {
						sidePanel.appendText(label + "->\n");
					});

					drawPathTo(currPoint.getTranslateX(), currPoint.getTranslateY(), path.peek().getTranslateX(),
							path.peek().getTranslateY(), PATH_COLOR, SOLID_PATH_W, false);
				}

				currPoint = path.pop();
				String label = currPoint.label;
				double totalWeight = weight;
				int totalHop = hop;
				int totalCycle = cycle;
				Platform.runLater(() -> {
					sidePanel.appendText(label + "\n" + "Path total: " + totalWeight + "\nStop: " + totalHop
							+ "\nCycles: " + totalCycle);
				});

				setControlDisable(false);
				log.info("Path found!");
			}
		});

		simulation.start();
	}

	/**
	 * Helper method to draw an edge between two points.
	 * 
	 * @param probe whether this is a explore path or a path that has been taken.
	 */
	private void drawPathTo(final double startX, final double startY, final double endX, final double endY, Color c,
			double strokeWidth, boolean probe) {
		// Draw line
		Line line = new Line(startX, startY, startX, startY);
		line.setStroke(c);
		line.setStrokeWidth(strokeWidth);
		if (probe)
			probeLines.add(line);
		else
			pathLines.add(line);

		if (animationOn) {
			Timeline t = new Timeline();
			KeyValue kv1 = new KeyValue(line.endXProperty(), endX), kv2 = new KeyValue(line.endYProperty(), endY);
			KeyFrame kf1 = new KeyFrame(Duration.millis(ANIME_DURATION), kv1),
					kf2 = new KeyFrame(Duration.millis(ANIME_DURATION), kv2);

			t.getKeyFrames().addAll(kf1, kf2);
			Platform.runLater(() -> {
				mainPanel.getChildren().add(line);
				if (probe)
					line.toBack();
				t.play();
			});
		} else {
			Platform.runLater(() -> {
				line.setEndX(endX);
				line.setEndY(endY);
				mainPanel.getChildren().add(line);
				if (probe)
					line.toBack();
			});
		}
	}

	/**
	 * Reset main panel.
	 */
	private void resetMainPanel() {
		setControlDisable(true);
		edgeList.clear();
		pathLines.clear();
		probeLines.clear();
		points.clear();
		startPoint = null;
		endPoint = null;
		isChoosingStart = false;
		isChoosingEnd = false;

		Platform.runLater(() -> {
			mainPanel.getChildren().clear();
			openFileButton.setDisable(false);
		});
	}

	/**
	 * Arrange all Side panel components.
	 * 
	 * @param root root panel
	 */
	private void layoutSidePanel(BorderPane root) {
		// Side panel stuff
		sidePanel = new TextArea();
		sidePanel.setPrefSize(SIDE_WIDTH, HEIGHT);
		sidePanel.setStyle("-fx-border-width: 0 0 0 1px; -fx-border-style: solid;");

		root.setRight(sidePanel);
	}

	/**
	 * Helper method to (un)disable all control areas.
	 * 
	 * @param stat disable or not
	 */
	private void setControlDisable(boolean stat) {
		Platform.runLater(() -> {
			for (Control c : controls)
				c.setDisable(stat);
		});
	}

	/**
	 * Driver method.
	 */
	public static void main(String[] args) {
		Application.launch(args);
	}

	/**
	 * Inner class for a point(vertex).
	 * 
	 * @author Hackerry
	 *
	 */
	private class Point extends Circle {
		private String label;
		private double x, y;
		private int id;
		private boolean isStart = false, isEnd = false, isVisited = false;
		private Point prev;
		private double weight;
		private double estimateDist;

		Point(double x, double y, String label, int id, Text infoText) {
			this.x = x;
			this.y = y;
			this.id = id;
			this.setCenterX(0);
			this.setCenterY(0);
			this.setRadius(NORMAL_R);
			this.label = label;
			this.prev = null;
			this.weight = Double.MAX_VALUE;
			this.estimateDist = Double.MAX_VALUE;
			this.setOnMouseEntered(e -> {
				Platform.runLater(() -> {
					infoText.setText(label);
					this.setRadius(BIG_R);
					this.setCursor(Cursor.HAND);
				});
			});
			this.setOnMouseExited(e -> {
				Platform.runLater(() -> {
					if (!isStart && !isEnd)
						this.setRadius(NORMAL_R);
					this.setCursor(Cursor.DEFAULT);
				});
			});
			this.setOnMouseClicked(e -> {
				if (isChoosingStart) {
					isChoosingStart = false;
					setControlDisable(false);
					if (startPoint != null) {
						// Attempt to assign start = end
						if (endPoint == this)
							return;
						startPoint.setStart(false);
					}
					startPoint = this;
					this.setStart(true);
				} else if (isChoosingEnd) {
					isChoosingEnd = false;
					setControlDisable(false);
					if (endPoint != null) {
						// Attempt to assign start = end
						if (startPoint == this)
							return;
						endPoint.setEnd(false);
					}
					endPoint = this;
					this.setEnd(true);
				}
			});
		}

		public void setStart(boolean start) {
			isStart = start;

			Platform.runLater(() -> {
				if (start) {
					this.setRadius(BIG_R);
					this.setFill(Color.DARKRED);
				} else {
					this.setRadius(NORMAL_R);
					this.setFill(Color.BLACK);
				}
			});
		}

		public void setEnd(boolean end) {
			isEnd = end;

			Platform.runLater(() -> {
				if (end) {
					this.setRadius(BIG_R);
					this.setFill(Color.LIMEGREEN);
				} else {
					this.setRadius(NORMAL_R);
					this.setFill(Color.BLACK);
				}
			});
		}
	}

	private class DijkstraComparator implements Comparator<Point> {
		@Override
		public int compare(Point p1, Point p2) {
			return p1.weight < p2.weight ? -1 : (p1.weight > p2.weight ? 1 : 0);
		}
	}

	private class AStarComparator implements Comparator<Point> {
		@Override
		public int compare(Point p1, Point p2) {
			double p1Weight = p1.weight + p1.estimateDist;
			double p2Weight = p2.weight + p2.estimateDist;
			return p1Weight < p2Weight ? -1 : (p1Weight > p2Weight ? 1 : 0);
		}
	}
}