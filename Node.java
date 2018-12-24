public class Node {
    private int row, col;
    private Node prev;
    private boolean isVisited, isWall, isStart, isEnd, onPath;
    
    public Node(int row, int col) {
        this.row = row;
        this.col = col;
    }
   
    public int getRow() {
        return row;
    }

    public int getCol() {
        return col;
    }

    public Node getPrev() {
        return prev;
    }
    
    public boolean isVisited() {
        return isVisited;
    }
    
    public boolean isWall() {
        return isWall;
    }
    
    public void setWall(boolean isWall) {
        this.isWall = isWall;
    }
    
    public void toggleWall() {
        isWall = !isWall;
    }
    
    public boolean isStart() {
        return isStart;
    }

    public boolean isEnd() {
        return isEnd;
    }

    public void setVisited(boolean isVisited) {
        this.isVisited = isVisited;
    }

    public void setStart(boolean isStart) {
        this.isStart = isStart;
    }
    
    public void setEnd(boolean isEnd) {
        this.isEnd = isEnd;
    }
    
    public void setPrev(Node prev) {
        this.prev = prev;
    }
    
    public void setOnPath(boolean onPath) {
        this.onPath = onPath;
    }
    
    public boolean isOnPath() {
        return onPath;
    }
    
    public String toString() {
        return "[" + row + "," + col + "]";
    }
}
