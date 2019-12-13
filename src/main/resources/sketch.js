
function setup() {
    createCanvas(windowWidth, windowHeight);
    frameRate(1);

    grid.resize();
}

function draw() {
    const states = clusterStatus().states;

    grid.draw(color(43, 52, 58));

    frame(0, 0, 18, 42);
    Label().setX(0).setY(0).setW(18).setH(2)
            .setBorder(0.3)
            .setKey("Cluster Status")
            .setValue("Offline")
            .setBgColor(color(100, 75))
            .setKeyColor(color(29, 249, 246))
            .setValueColor(color(255))
            .draw();
    nineNodes(0, 24, 18, 4, states);

    strokeWeight(2.5);
    stroke(150, 150);
    line(grid.toX(19), grid.toY(0), grid.toX(19 + 3 * 18 + 2), grid.toY(0));
    Label().setX(19).setY(0).setW(19 * 2 + 18).setH(1)
            .setBorder(0.1)
            .setKey("Cluster Nodes")
            .setValue("9 Up, 0 Offline")
            .setBgColor(color(100, 75))
            .setKeyColor(color(29, 249, 246))
            .setValueColor(color(255))
            .draw();

    frame(1 * 19, 0 * 14 + 1, 18, 13);
    frame(2 * 19, 0 * 14 + 1, 18, 13);
    frame(3 * 19, 0 * 14 + 1, 18, 13);

    frame(1 * 19, 1 * 14 + 1, 18, 13);
    frame(2 * 19, 1 * 14 + 1, 18, 13);
    frame(3 * 19, 1 * 14 + 1, 18, 13);

    frame(1 * 19, 2 * 14 + 1, 18, 13);
    frame(2 * 19, 2 * 14 + 1, 18, 13);
    frame(3 * 19, 2 * 14 + 1, 18, 13);

    nineNodes(1 * 19 + 9, 5, 9, 2, states);
    nineNodes(2 * 19 + 9, 5, 9, 2, states);
    nineNodes(3 * 19 + 9, 5, 9, 2, states);

    nineNodes(1 * 19 + 9, 19, 9, 2, states);
    nineNodes(2 * 19 + 9, 19, 9, 2, states);
    nineNodes(3 * 19 + 9, 19, 9, 2, states);

    nineNodes(1 * 19 + 9, 33, 9, 2, states);
    nineNodes(2 * 19 + 9, 33, 9, 2, states);
    nineNodes(3 * 19 + 9, 33, 9, 2, states);
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
    stroke(150, 150);

    line(xl - offset, yt, xr + offset, yt); // top horizontal
    line(xl - offset, yb, xr + offset, yb); // bottom horizontal
    line(xl, yt - offset, xl, yb + offset); // left vertical
    line(xr, yt - offset, xr, yb + offset); // right vertical
}

function nineNodes(x, y, size, border, nodes) {
    const nodeSize = size / 3;
    for (var row = 0; row < 3; row++) {
        for (var col = 0; col < 3; col++) {
            drawNode(x + nodeSize * col, y + nodeSize * row, nodeSize, border, nodes[row * 3 + col]);
        }
    }
}

function drawNode(x, y, size, border, state) {
    const sideLength = grid.toLength(size) - border * 2;

    strokeWeight(0);
    fill(nodeColor(state));
    rect(grid.toX(x) + border, grid.toY(y) + border, sideLength, sideLength);
}

const nodeColors = {
    offline: [100, 100, 100, 200],
    starting: [50, 170, 50, 200],
    up: [50, 255, 100, 200],
    stopping: [200, 200, 50, 200],
    unreachable: [255, 50, 50, 200],
}

function nodeColor(state) {
    return color(nodeColors[state]);
}

function clusterStatus() {
    const states = ['starting', 'up', 'stopping', 'offline', 'unreachable'];
    function state() {
        return states[Math.floor(Math.random() * states.length)];
    }

    return JSON.parse(
        '{' +
            '"states":' +
                '[' +
                    '"' + state() + '",' +
                    '"' + state() + '",' +
                    '"' + state() + '",' +
                    '"' + state() + '",' +
                    '"' + state() + '",' +
                    '"' + state() + '",' +
                    '"' + state() + '",' +
                    '"' + state() + '",' +
                    '"' + state() + '"' +
                ']' +
        '}'
    );
}

let = Label = function () {
    let x;
    let y;
    let w;
    let h;
    let border;
    let key = "";
    let value = "";
    let bgColor;
    let keyColor;
    let valueColor = color(0);

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
            fill(this.bgColor);
            rect(cx, cy, cw, ch);

            textSize(ch - cb * 2);

            textAlign(LEFT, CENTER);
            fill(this.keyColor);
            text(this.key, cx + cb, cy + ch / 2);

            if (!(this.valueColor === undefined) && !(this.value === undefined)) {
                textAlign(RIGHT, CENTER);
                fill(this.valueColor);
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
