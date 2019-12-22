
const clusterNodeRequestMsInterval = 250;
const drawFPS = 1000 / clusterNodeRequestMsInterval;

function setup() {
    createCanvas(windowWidth, windowHeight);
    frameRate(drawFPS);

    grid.resize();

    requestClusterState();
}

function draw() {
    grid.draw(color(43, 52, 58));

    drawSummary();
    drawNineNodes();
}

function drawSummary() {
    frame(0, 0, 18, 42);

    Label().setX(0).setY(0).setW(18).setH(2)
            .setBorder(0.3)
            .setKey("Cluster Status")
            .setValue(inState("up") > 0 ? "Online" : "Offline")
            .setBgColor(color(100, 75))
            .setKeyColor(color(29, 249, 246))
            .setValueColor(color(255))
            .draw();

    Label().setX(1).setY(3).setW(10).setH(1.9)
            .setBorder(0.3)
            .setKey("Leader")
            .setValue(clusterState.summary.leader)
            .setBgColor(isGossipConvergenceNotPossible() ? color(255, 55, 55, 100) : color(100, 75))
            .setKeyColor(color(255, 191, 0))
            .setValueColor(color(255))
            .draw();

    Label().setX(1).setY(5).setW(10).setH(1.9)
            .setBorder(0.3)
            .setKey("Oldest")
            .setValue(clusterState.summary.oldest)
            .setBgColor(color(100, 75))
            .setKeyColor(color(255, 191, 0))
            .setValueColor(color(255))
            .draw();

    nineNodes(0, 24, 18, 0.05, clusterState.summary.nodes);
}

function drawNineNodes() {
    strokeWeight(2.5);
    stroke(255, 100);
    line(grid.toX(19), grid.toY(0), grid.toX(19 + 3 * 18 + 2), grid.toY(0));
    Label().setX(19).setY(0).setW(19 * 2 + 18).setH(1)
            .setBorder(0.1)
            .setKey("Cluster Nodes")
            .setValue(inState("up") + " Online")
            .setBgColor(color(100, 75))
            .setKeyColor(color(29, 249, 246))
            .setValueColor(color(255))
            .draw();

    for (row = 0; row < 3; row++) {
        for (col = 0; col < 3; col++) {
            const x = (col + 1) * 19;
            const y = row * 14 + 1
            const nodeNo = row * 3 + col;
            frame(x, y, 18, 13);
            nodeDetails(x, y, 18, 13, nodeNo);
        }
    }

    for (row = 0; row < 3; row++) {
        for (col = 0; col < 3; col++) {
            nineNodes((col + 1) * 19 + 9, row * 14 + 5, 9, 0.025, clusterState.members[row * 3 + col].nodes);
        }
    }
}

function windowResized() {
    resizeCanvas(windowWidth, windowHeight);

    grid.resize();
}

function aspectratio(width, height) {
    return height / width;
}

const grid = {
    borderWidth: 20,
    ticksHorizontal: 76,
    ticksVertical: aspectratio(16, 9) * 76, // ticksHorizontal
    tickWidth: 0,
    resize: function() {
        gridWidth = windowWidth - 2 * this.borderWidth;
        this.tickWidth = gridWidth / this.ticksHorizontal;
    },
    toX: function(gridX) { // convert from grid scale to canvas scale
        return this.borderWidth + gridX * this.tickWidth;
    },
    toY: function(gridY) {
        return this.borderWidth + gridY * this.tickWidth;
    },
    toLength: function(gridLength) {
        return gridLength * this.tickWidth
    },
    draw : function(bgColor) {
        var xEven = true;
        var yEven = true;

        background(bgColor);

        for (var x = 0; x < this.ticksHorizontal; x++ ) {
            for (var y  = 0; y < this.ticksVertical; y++) {
                drawTick(x, y, xEven, yEven, this.tickWidth);
                yEven = !yEven;
            }
            xEven = !xEven;
        }

        function drawTick(x, y, xEven, yEven, tickWidth) {
            if (xEven && yEven) {
                strokeWeight(tickWidth / 8);
                stroke(100);
                point(grid.toX(x), grid.toY(y));
            } else {
                strokeWeight(tickWidth / 12);
                stroke(100);
                point(grid.toX(x), grid.toY(y));
            }
        }
    }
}

function frame(x, y, width, height) {
    const offset = grid.tickWidth / 5;

    const xl = grid.toX(x);
    const xr = grid.toX(x + width);
    const yt = grid.toY(y);
    const yb = grid.toY(y + height);

    strokeWeight(2.5);
    stroke(255, 100);

    line(xl - offset, yt, xr + offset, yt); // top horizontal
    line(xl - offset, yb, xr + offset, yb); // bottom horizontal
    line(xl, yt - offset, xl, yb + offset); // left vertical
    line(xr, yt - offset, xr, yb + offset); // right vertical
}

function nodeDetails(x, y, w, h, nodeNo) {
    const port = 2551 + nodeNo;
    const node = clusterState.members[nodeNo].nodes[nodeNo];
    if (node.state != "offline") {
        Label().setX(x).setY(y).setW(w).setH(1.5)
                .setBorder(0.2)
                .setKey("" + port)
                .setValue(node.memberState)
                .setBgColor(color(100, 75))
                .setKeyColor(color(255, 191, 0))
                .setValueColor(color(255))
                .draw();

    }
}

function nineNodes(x, y, size, border, nodes) {
    const nodeSize = size / 3;
    for (var row = 0; row < 3; row++) {
        for (var col = 0; col < 3; col++) {
            drawNode(x + nodeSize * col, y + nodeSize * row, nodeSize, border, nodes[row * 3 + col]);
        }
    }
}

function drawNode(x, y, size, border, node) {
    const sideLength = grid.toLength(size - border * 4);

    strokeWeight(0);
    fill(nodeColor(node.state));
    rect(grid.toX(x + border * 2), grid.toY(y + border * 2), sideLength, sideLength);

    if (!(node.state == "offline")) {
        drawNodePort(x, y, size, border, node);
        drawNodeIfLeader(x, y, size, border, node);
        drawNodeIfOldest(x, y, size, border, node);
    }
}

function drawNodePort(x, y, size, border, node) {
    Label().setX(x + border * 2)
            .setY(y + border * 2)
            .setW(size - border * 2)
            .setH(size / 5.5)
            .setBorder(border)
            .setKey(node.port)
            .setKeyColor(color(255))
            .draw();
    Label().setX(x + border * 2)
            .setY(y + size / 6 + border * 2)
            .setW(size - border * 2)
            .setH(size / 6)
            .setBorder(border)
            .setValue(node.memberState)
            .setValueColor(color(255))
            .draw();
}

function drawNodeIfLeader(x, y, size, border, node) {
    if (node.leader) {
        Label().setX(x + border * 2)
                .setY(y + size / 2 - border * 2)
                .setW(size - border)
                .setH(size / 2)
                .setBorder(border)
                .setKey("L")
                .setKeyColor(isGossipConvergenceNotPossible() ? color(255, 75, 75) : color(255))
                .draw();
    }
}

function drawNodeIfOldest(x, y, size, border, node) {
    if (node.oldest) {
        Label().setX(x + border * 2)
                .setY(y + size / 2 - border * 4)
                .setW(size - border * 4)
                .setH(size / 2)
                .setBorder(border)
                .setValue("O")
                .setValueColor(color(255))
                .draw();
    }
}

const nodeColors = {
    offline: [100, 100, 100, 200],
    starting: [50, 170, 50, 200],
    up: [50, 255, 100, 200],
    stopping: [200, 200, 50, 200],
    unreachable: [255, 50, 50, 200],
    down: [255, 255, 255, 200] // TOTO down not yet implemented
}

function nodeColor(state) {
    return color(nodeColors[state]);
}

let = Label = function () {
    return {
        setX: function(x) { this.x = x; return this; },
        setY: function(y) { this.y = y; return this; },
        setW: function(w) { this.w = w; return this; },
        setH: function(h) { this.h = h; return this; },
        setBorder: function(b) { this.border = b; return this; },
        setKey: function(k) { this.key = k; return this; },
        setValue: function(v) { this.value = v; return this; },
        setBgColor: function(c) { this.bgColor = c; return this; },
        setKeyColor: function(c) { this.keyColor = c; return this; },
        setValueColor: function(c) { this.valueColor = c; return this; },
        draw: function() {
            const cx = grid.toX(this.x);
            const cy = grid.toY(this.y);
            const cw = grid.toLength(this.w);
            const ch = grid.toLength(this.h);
            const cb = grid.toLength(this.border);

            strokeWeight(0);
            fill(this.bgColor || color(0, 0));
            rect(cx, cy, cw, ch);

            textSize(ch - cb * 2);

            if (this.key) {
                textAlign(LEFT, CENTER);
                fill(this.keyColor || color(0, 0));
                text(this.key, cx + cb, cy + ch / 2);
            }

            if (this.value) {
                textAlign(RIGHT, CENTER);
                fill(this.valueColor || color(0, 0));
                text(this.value, cx + cw - cb, cy + ch / 2);
            }
        },
        Label: function() {
            if (!(this instanceof Label)) {
                return new Label();
            }
        }
    };
};

function timeNow() {
    return (new Date()).toISOString().substr(11, 12);
}

function requestClusterState() {
    setInterval(requestClusterStateInterval, clusterNodeRequestMsInterval);
}

function requestClusterStateInterval() {
    //console.log(timeNow(), "interval");

    clusterStateScanAllForDeadNodes();

    for (var port = 8551; port <= 8559; port++) {
        requestClusterStateFromNode(port);
    }
}

function requestClusterStateFromNode(port) {
    const url = "http://localhost:" + port + "/cluster-state";

    loadJSON(url, clusterStateUpdateNode, requestClusterStateFromNodeError);
}

const clusterState = clusterStateInit();

function clusterStateNodeReset(nodes) {
    const time = (new Date()).getTime();

    for (var n = 0; n < 9; n++) {
        clusterStateNodeInit(n);
    }
}

function clusterStateScanForDeadNodes(nodes) {
    const time = (new Date()).getTime();

    for (var n = 0; n < 9; n++) {
        if (time - nodes[n].time > 3000) { // node is dead if no update for over 3 seconds
            nodes[n] = clusterStateNodeInit(n + 2551);
        }
    }
}

function clusterStateScanAllForDeadNodes() {
    clusterStateScanForDeadNodes(clusterState.summary.nodes);
    for (var m = 0; m < 9; m++) {
        clusterStateScanForDeadNodes(clusterState.members[m].nodes);
    }
}

function clusterStateInit() {
    const time = (new Date()).getTime();
    const clusterState = {};
    clusterState.summary = {};
    clusterState.summary.leader = 0;
    clusterState.summary.oldest = 0;
    clusterState.summary.nodes = [];

    for (var node = 0; node < 9; node++) {
        clusterState.summary.nodes[node] = clusterStateNodeInit(node + 2551);
    }

    clusterState.members = [];

    for (var member = 0; member < 9; member++) {
        clusterState.members[member] = { nodes: [] };

        for (var node = 0; node < 9; node++) {
            clusterState.members[member].nodes[node] = clusterStateNodeInit(node + 2551);
        }
    }
    return clusterState;
}

function clusterStateNodeInit(port) {
    return { node: port, state: "offline", memberState: "unknown", leader: false, oldest: false, time: (new Date()).getTime() };
}

function clusterStateUpdateNode(clusterStateFromNode) {
    const selfPort = clusterStateFromNode.selfPort;

    clusterStateNodeReset(clusterState.members[selfPort - 2551].nodes);

    for (var n = 0; n < clusterStateFromNode.nodes.length; n++) {
        const port = clusterStateFromNode.nodes[n].port;
        const node = clusterStateFromNode.nodes[n];
        node.time = (new Date()).getTime();
        clusterState.members[selfPort - 2551].nodes[port - 2551] = node;
    }

    clusterStateUpdateSummary(clusterStateFromNode);
}

function clusterStateUpdateSummary(clusterStateFromNode) {
    if (clusterStateFromNode.leader) {
        clusterState.summary.leader = clusterStateFromNode.selfPort;

        for (var n = 0; n < clusterStateFromNode.nodes.length; n++) {
            const port = clusterStateFromNode.nodes[n].port;
            const node = clusterStateFromNode.nodes[n];
            node.time = (new Date()).getTime();
            clusterState.summary.nodes[port - 2551] = node;
        }
    }

    if (clusterStateFromNode.oldest) { // TODO fix this - oldest should be derived from the leader cluster state
        clusterState.summary.oldest = clusterStateFromNode.selfPort;
    }

    summaryStates = nodeStates(clusterState.summary.nodes);
    memberStates = nodeStates(clusterState.members[0].nodes);
}

function requestClusterStateFromNodeError(response) {
    //console.log(timeNow(), response);
}

function nodeStates(nodes) {
    states = [];
    for (var n = 0; n < 9; n++) {
        states[n] = nodes[n].state;
    }
    return states;
}

function inState(state) {
    return clusterState.summary.nodes.filter(s => s.state == state).length;
}

//labelNodes(26, 2, 8, 2, 0.02, 2551);

function isGossipConvergenceNotPossible() {
    return !(undefined === clusterState.summary.nodes.find(node => node.memberState == "unreachable"));
}
