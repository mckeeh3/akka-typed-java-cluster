package cluster;

import akka.actor.typed.ActorSystem;
import akka.cluster.ClusterEvent;
import akka.cluster.Member;
import akka.cluster.MemberStatus;
import akka.cluster.typed.Cluster;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.*;
import akka.stream.ActorMaterializer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.slf4j.Logger;
import scala.Option;

import java.io.*;
import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

class HttpServer {
    private final int port;
    private final ActorSystem<Void> actorSystem;
    private final ActorMaterializer actorMaterializer;

    private HttpServer(int port, ActorSystem<Void> actorSystem) {
        this.port = port;
        this.actorSystem = actorSystem;

        actorMaterializer = ActorMaterializer.create(actorSystem.classicSystem());

        startHttpServer();
    }

    static void start(ActorSystem<Void> actorSystem) {
        Option<Object> portOption = Cluster.get(actorSystem).selfMember().address().port();
        if (portOption.isDefined()) {
            int port = Integer.parseInt(portOption.get().toString());
            if (port >= 2551 && port <= 2559) {
                new HttpServer(port + 7000, actorSystem);
            }
        }
    }

    private void startHttpServer() {
        try {
            CompletionStage<ServerBinding> serverBindingCompletionStage = Http.get(actorSystem.classicSystem())
                    .bindAndHandleSync(this::handleHttpRequest, ConnectHttp.toHost("127.0.0.1", port), actorMaterializer);

            serverBindingCompletionStage.toCompletableFuture().get(15, TimeUnit.SECONDS);
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            log().error("Monitor HTTP server error", e);
        } finally {
            log().info("HTTP server started on port {}", port);
        }
    }

    private HttpResponse handleHttpRequest(HttpRequest httpRequest) {
        //log().info("HTTP request '{}'", httpRequest.getUri().path());
        switch (httpRequest.getUri().path()) {
            case "/":
                return htmlFileResponse("dashboard.html");
            case "/dashboard.js":
                return jsFileResponse("dashboard.js");
            case "/p5.js":
                return jsFileResponse("p5.js");
            case "/p5.sound.js":
                return jsFileResponse("p5.sound.js");
            case "/cluster-state":
                return jsonResponse();
            default:
                return HttpResponse.create().withStatus(404);
        }
    }

    private HttpResponse htmlFileResponse(String filename) {
        try {
            String fileContents = readFile(filename);
            return HttpResponse.create()
                    .withEntity(ContentTypes.TEXT_HTML_UTF8, fileContents)
                    .withStatus(StatusCodes.ACCEPTED);
        } catch (IOException e) {
            log().error(String.format("I/O error on file '%s'", filename), e);
            return HttpResponse.create().withStatus(StatusCodes.INTERNAL_SERVER_ERROR);
        }
    }

    private HttpResponse jsFileResponse(String filename) {
        try {
            String fileContents = readFile(filename);
            return HttpResponse.create()
                    .withEntity(ContentTypes.create(MediaTypes.APPLICATION_JAVASCRIPT, HttpCharsets.UTF_8), fileContents)
                    .withStatus(StatusCodes.ACCEPTED);
        } catch (IOException e) {
            log().error(String.format("I/O error on file '%s'", filename), e);
            return HttpResponse.create().withStatus(StatusCodes.INTERNAL_SERVER_ERROR);
        }
    }

    private HttpResponse jsonResponse() {
        try {
            String jsonContents = loadNodes(actorSystem).toJson();
            return HttpResponse.create()
                    .withEntity(ContentTypes.create(MediaTypes.APPLICATION_JAVASCRIPT, HttpCharsets.UTF_8), jsonContents)
                    .withHeaders(Collections.singletonList(HttpHeader.parse("Access-Control-Allow-Origin", "*")))
                    .withStatus(StatusCodes.ACCEPTED);
        } catch (Exception e) {
            log().error("I/O error on JSON response");
            return HttpResponse.create().withStatus(StatusCodes.INTERNAL_SERVER_ERROR);
        }
    }

    private String readFile(String filename) throws IOException {
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(filename);
        if (inputStream == null) {
            throw new FileNotFoundException(String.format("Filename '%s'", filename));
        } else {
            StringBuilder fileContents = new StringBuilder();

            try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream))) {
                String line;
                while ((line = br.readLine()) != null) {
                    fileContents.append(String.format("%s%n", line));
                }
            }
            return fileContents.toString();
        }
    }

    private static Nodes loadNodes(ActorSystem<Void> actorSystem) {
        final Cluster cluster = Cluster.get(actorSystem);

        ClusterEvent.CurrentClusterState clusterState = cluster.state();

        Set<Member> unreachable = clusterState.getUnreachable();

        Optional<Member> old = StreamSupport.stream(clusterState.getMembers().spliterator(), false)
                .filter(member -> member.status().equals(MemberStatus.up()))
                .filter(member -> !(unreachable.contains(member)))
                .reduce((older, member) -> older.isOlderThan(member) ? older : member);

        Member oldest = old.orElse(cluster.selfMember());

        List<Integer> seedNodePorts = seedNodePorts(actorSystem);

        final Nodes nodes = new Nodes(
                memberPort(cluster.selfMember()),
                cluster.selfMember().address().equals(clusterState.getLeader()),
                oldest.equals(cluster.selfMember()));

        StreamSupport.stream(clusterState.getMembers().spliterator(), false)
                .forEach(new Consumer<Member>() {
                    @Override
                    public void accept(Member member) {
                        nodes.add(member, leader(member), oldest(member), seedNode(member));
                    }

                    private boolean leader(Member member) {
                        return member.address().equals(clusterState.getLeader());
                    }

                    private boolean oldest(Member member) {
                        return oldest.equals(member);
                    }

                    private boolean seedNode(Member member) {
                        return seedNodePorts.contains(memberPort(member));
                    }
                });

        clusterState.getUnreachable()
                .forEach(nodes::addUnreachable);

        return nodes;
    }

    private Logger log() {
        return actorSystem.log();
    }

    private static boolean isValidPort(int port) {
        return port >= 2551 && port <= 2559;
    }

    private static int memberPort(Member member) {
        Option<Object> portOption = member.address().port();
        if (portOption.isDefined()) {
            return Integer.parseInt(portOption.get().toString());
        }
        return 0;
    }

    private static List<Integer> seedNodePorts(ActorSystem<Void> actorSystem) {
        return actorSystem.settings().config().getList("akka.cluster.seed-nodes")
                .stream().map(s -> s.unwrapped().toString())
                .map(s -> {
                    String[] split = s.split(":");
                    return split.length == 0 ? 0 : Integer.parseInt(split[split.length - 1]);
                }).collect(Collectors.toList());
    }

    public static class Nodes implements Serializable {
        public final int selfPort;
        public final boolean leader;
        public final boolean oldest;
        public List<Node> nodes = new ArrayList<>();

        public Nodes(int selfPort, boolean leader, boolean oldest) {
            this.selfPort = selfPort;
            this.leader = leader;
            this.oldest = oldest;
        }

        void add(Member member, boolean leader, boolean oldest, boolean seedNode) {
            final int port = memberPort(member);
            if (isValidPort(port)) {
                nodes.add(new Node(port, state(member.status()), memberStatus(member.status()), leader, oldest, seedNode));
            }
        }

        void addUnreachable(Member member) {
            final int port = memberPort(member);
            if (isValidPort(port)) {
                Node node = new Node(port, "unreachable", "unreachable", false, false, false);
                nodes.remove(node);
                nodes.add(node);
            }
        }

        private static String state(MemberStatus memberStatus) {
            if (memberStatus.equals(MemberStatus.down())) {
                return "down";
            } else if (memberStatus.equals(MemberStatus.joining())) {
                return "starting";
            } else if (memberStatus.equals(MemberStatus.weaklyUp())) {
                return "starting";
            } else if (memberStatus.equals(MemberStatus.up())) {
                return "up";
            } else if (memberStatus.equals(MemberStatus.exiting())) {
                return "stopping";
            } else if (memberStatus.equals(MemberStatus.leaving())) {
                return "stopping";
            } else if (memberStatus.equals(MemberStatus.removed())) {
                return "stopping";
            } else {
                return "offline";
            }
        }

        private static String memberStatus(MemberStatus memberStatus) {
            if (memberStatus.equals(MemberStatus.down())) {
                return "down";
            } else if (memberStatus.equals(MemberStatus.joining())) {
                return "joining";
            } else if (memberStatus.equals(MemberStatus.weaklyUp())) {
                return "weaklyup";
            } else if (memberStatus.equals(MemberStatus.up())) {
                return "up";
            } else if (memberStatus.equals(MemberStatus.exiting())) {
                return "exiting";
            } else if (memberStatus.equals(MemberStatus.leaving())) {
                return "leaving";
            } else if (memberStatus.equals(MemberStatus.removed())) {
                return "removed";
            } else {
                return "unknown";
            }
        }

        String toJson() {
            ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
            try {
                return ow.writeValueAsString(this);
            } catch (JsonProcessingException e) {
                return String.format("{ \"error\" : \"%s\" }", e.getMessage());
            }
        }
    }

    public static class Node implements Serializable {
        public final int port;
        public final String state;
        public final String memberState;
        public final boolean leader;
        public final boolean oldest;
        public final boolean seedNode;

        public Node(int port, String state, String memberState, boolean leader, boolean oldest, boolean seedNode) {
            this.port = port;
            this.state = state;
            this.memberState = memberState;
            this.leader = leader;
            this.oldest = oldest;
            this.seedNode = seedNode;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Node node = (Node) o;
            return Objects.equals(port, node.port);
        }

        @Override
        public int hashCode() {
            return Objects.hash(port);
        }
    }
}