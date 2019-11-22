## Akka Java Cluster Example

### Introduction

This is a Java, Maven, Akka project that demonstrates how to setup a basic
[Akka Cluster](https://doc.akka.io/docs/akka/current/index-cluster.html).

This project is one in a series of projects that starts with a simple Akka Cluster project and progressively builds up to examples of event sourcing and command query responsibility segregation.

The project series is composed of the following GitHub repos:
* [akka-typed-java-cluster](https://github.com/mckeeh3/akka-typed-java-cluster) (this project)
* [akka-typed-java-cluster-aware](https://github.com/mckeeh3/akka-typed-java-cluster-aware)
* [akka-typed-java-cluster-singleton](https://github.com/mckeeh3/akka-typed-java-cluster-singleton)
* [akka-typed-java-cluster-sharding](https://github.com/mckeeh3/akka-typed-java-cluster-sharding)
* [akka-typed-java-cluster-persistence](https://github.com/mckeeh3/akka-typed-java-cluster-persistence)
* [akka-typed-java-cluster-persistence-query](https://github.com/mckeeh3/akka-typed-java-cluster-persistence-query)

Each project can be cloned, built, and runs independently of the other projects.

### About Akka Clustering

According to the [Akka documentation](https://doc.akka.io/docs/akka/current/common/cluster.html),
"*Akka Cluster provides a fault-tolerant decentralized peer-to-peer based cluster membership service with no single point of failure or single point of bottleneck. It does this using gossip protocols and an automatic failure detector.*

*Akka cluster allows for building distributed applications, where one application or service spans multiple nodes.*"

The above paragraphs from the Akka documentation are packed with a lot of concepts that initially may be hard to wrap your head around. Consider some of the terms that were thrown out in just two sentences, terms like "fault-tolerant," "decentralized," "peer-to-peer" and "no single point of failure." The last sentence almost casually states "*where one application or service spans multiple nodes*." Wait; what? How does an application or service span multiple nodes?

The answer is that Akka provides an abstraction layer that is composed of actors interacting with each other in an actor system. Akka is an implementation of the actor model.
The actor model "*[(Wikipedia)](https://en.wikipedia.org/wiki/Actor_model) treats "actors" as the universal primitives of concurrent computation. In response to a message that it receives, an actor can: make local decisions, create more actors, send more messages, and determine how to respond to the next message received. Actors may modify their own private state, but can only affect each other through messages (avoiding the need for any locks).*"

Akka actors communicate with each other via asynchronous messages. Akka actors systems run on Java Virtual Machines, and with Akka clusters, a single actor system may logically span multiple networked JVMs. This networked actor system abstraction layer makes it possible for actors to transparently communicate with each across a cluster of nodes. One way to think of this is that from the perspective of actors, they live in an actor system, the fact that the actor system is running on one or more nodes is, for the most part, hidden within the abstraction layer.

### The ClusterListenerActor Actor

Akka actors are implemented in Java or Scala. You create actors as Java or Scala classes. There are two ways to implement actors, either untyped and typed. Untyped actors are used in this Akka Java cluster example project series.

The Akka documentation section about
[Actors](https://doc.akka.io/docs/akka/current/actors.html#actors)
is a good starting point for those of you that are interested in diving into the details of how actors work and how they are implemented.

The first actor we will look at is named ClusterListenerActor. This actor is set up to receive messages about cluster events.  As nodes join and leave the cluster, this actor receives messages about these events. Theses received messages are then written to a logger.

The ClusterListenerActor provides a simple view of cluster activity.
Here is an example of the log output:
~~~
03:20:29.569 INFO  cluster-akka.actor.default-dispatcher-4 akka.tcp://cluster@127.0.0.1:2551/user/clusterListener - MemberUp(Member(address = akka.tcp://cluster@127.0.0.1:2553, status = Up)) sent to Member(address = akka.tcp://cluster@127.0.0.1:2551, status = Up)
03:20:29.570 INFO  cluster-akka.actor.default-dispatcher-4 akka.tcp://cluster@127.0.0.1:2551/user/clusterListener - 1 (LEADER) (OLDEST) Member(address = akka.tcp://cluster@127.0.0.1:2551, status = Up)
03:20:29.570 INFO  cluster-akka.actor.default-dispatcher-4 akka.tcp://cluster@127.0.0.1:2551/user/clusterListener - 2 Member(address = akka.tcp://cluster@127.0.0.1:2552, status = Up)
03:20:29.570 INFO  cluster-akka.actor.default-dispatcher-4 akka.tcp://cluster@127.0.0.1:2551/user/clusterListener - 3 Member(address = akka.tcp://cluster@127.0.0.1:2553, status = Joining)
~~~

Let's start with the full ClusterListenerActor source file. Note that this actor is implemented as a single Java class that extends an Akka based class.

~~~java
package cluster;

import akka.actor.AbstractLoggingActor;
import akka.actor.Cancellable;
import akka.actor.Props;
import akka.cluster.Cluster;
import akka.cluster.ClusterEvent;
import akka.cluster.ClusterEvent.CurrentClusterState;
import akka.cluster.Member;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.StreamSupport;

class ClusterListenerActor extends AbstractLoggingActor {
    private final Cluster cluster = Cluster.get(context().system());
    private Cancellable showClusterStateCancelable;

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(ShowClusterState.class, this::showClusterState)
                .matchAny(this::logClusterEvent)
                .build();
    }

    private void showClusterState(ShowClusterState showClusterState) {
        log().info("{} sent to {}", showClusterState, cluster.selfMember());
        logClusterMembers(cluster.state());
        showClusterStateCancelable = null;
    }

    private void logClusterEvent(Object clusterEventMessage) {
        log().info("{} sent to {}", clusterEventMessage, cluster.selfMember());
        logClusterMembers();
    }

    @Override
    public void preStart() {
        log().debug("Start");
        cluster.subscribe(self(), ClusterEvent.initialStateAsEvents(),
                ClusterEvent.ClusterDomainEvent.class);
    }

    @Override
    public void postStop() {
        log().debug("Stop");
        cluster.unsubscribe(self());
    }

    static Props props() {
        return Props.create(ClusterListenerActor.class);
    }

    private void logClusterMembers() {
        logClusterMembers(cluster.state());

        if (showClusterStateCancelable == null) {
            showClusterStateCancelable = context().system().scheduler().scheduleOnce(
                    Duration.ofSeconds(15),
                    self(),
                    new ShowClusterState(),
                    context().system().dispatcher(),
                    null);
        }
    }

    private void logClusterMembers(CurrentClusterState currentClusterState) {
        Optional<Member> old = StreamSupport.stream(currentClusterState.getMembers().spliterator(), false)
                .reduce((older, member) -> older.isOlderThan(member) ? older : member);

        Member oldest = old.orElse(cluster.selfMember());

        StreamSupport.stream(currentClusterState.getMembers().spliterator(), false)
                .forEach(new Consumer<Member>() {
                    int m = 0;

                    @Override
                    public void accept(Member member) {
                        log().info("{} {}{}{}", ++m, leader(member), oldest(member), member);
                    }

                    private String leader(Member member) {
                        return member.address().equals(currentClusterState.getLeader()) ? "(LEADER) " : "";
                    }

                    private String oldest(Member member) {
                        return oldest.equals(member) ? "(OLDEST) " : "";
                    }
                });
    }

    private static class ShowClusterState {
        @Override
        public String toString() {
            return ShowClusterState.class.getSimpleName();
        }
    }
}
~~~

This class is an example of a simple actor implementation. However, what is somewhat unique about this actor is that it subscribes to the Akka system to receive cluster event messages. Please see the Akka documentation
[Subscribe to Cluster Events](https://doc.akka.io/docs/akka/current/cluster-usage.html#subscribe-to-cluster-events)
for details. Here is the code that subscribes to cluster events.

~~~java
@Override
public void preStart() {
    log().debug("Start");
    cluster.subscribe(self(), ClusterEvent.initialStateAsEvents(),
            ClusterEvent.ClusterDomainEvent.class);
}
~~~

The actor is set up to receive cluster event messages. As these messages arrive the actor invokes methods written to log the event and log the current state of the cluster.

~~~java
@Override
public Receive createReceive() {
    return receiveBuilder()
            .match(ShowClusterState.class, this::showClusterState)
            .matchAny(this::logClusterEvent)
            .build();
}
~~~

As each node in the cluster starts up an instance of the ClusterListenerActor is started. The actor then logs cluster events as they occur in each node. You can examine the logs from each cluster node to review the cluster events and see the state of the cluster nodes, again from the perspective of each node.

### How it works

In this project, we are going to start with a basic template for an Akka, Java, and Maven based example that has the code and configuration for running an Akka Cluster. The Maven POM file uses two plugins, one for running the code using the `mvn:exec` command, and the other plugin builds a self contained JAR file for running the code using the `java -jar` command.

When the project code is executed the action starts in the `Runner` class `main` method.

~~~java
public static void main(String[] args) {
    if (args.length == 0) {
        startupClusterNodes(Arrays.asList("2551", "2552", "0"));
    } else {
        startupClusterNodes(Arrays.asList(args));
    }
}
~~~

The `main` method invokes the `startupClusterNodes` method passing it a list of ports. A default set of three ports is used if no arguments are provided.

~~~java
private static void startupClusterNodes(List<String> ports) {
    System.out.printf("Start cluster on port(s) %s%n", ports);

    ports.forEach(port -> {
        ActorSystem actorSystem = ActorSystem.create("cluster", setupClusterNodeConfig(port));

        AkkaManagement.get(actorSystem).start();

        actorSystem.actorOf(ClusterListenerActor.props(), "clusterListener");

        addCoordinatedShutdownTask(actorSystem, CoordinatedShutdown.PhaseClusterShutdown());

        actorSystem.log().info("Akka node {}", actorSystem.provider().getDefaultAddress());
    });
}
~~~

The `startupClusterNodes` methods loops through the list of ports. An actor system is created for each port.

~~~java
ActorSystem actorSystem = ActorSystem.create("cluster", setupClusterNodeConfig(port));
~~~

A lot happens when an actor system is created. Many of the details that determine how to run the actor system are defined via configuration settings. This project includes an `application.conf` configuration file, which is located in the `src/main/resources` directory. One of the most critical configuration settings defines the actor system host and port. When an actor system runs in a cluster, the configuration also defines how each node will locate and join the cluster. In this project, nodes join the cluster using what are called seed nodes.

~~~properties
cluster {
  seed-nodes = [
    "akka.tcp://cluster@127.0.0.1:2551",
    "akka.tcp://cluster@127.0.0.1:2552"]
}
~~~

Let's walk through a cluster startup scenario with this project. In this example, one JVM starts with no run time arguments. When the `Runner` class `main` method is invoked with no arguments the default is to create three actor systems on ports 2551, 2552, and port 0 (a zero port results in randomly selecting a non-zero port number).

As each actor system is created on a specific port, it looks at the seed node configuration settings. If the actor system's port is one of the seed nodes it knows that it will reach out to the other seed nodes with the goal of forming a cluster. If the actor system's port is not one of the seed nodes it will attempt to contact one of the seed nodes. The non-seed nodes need to announce themselves to one of the seed nodes and ask to join the cluster.

Here is an example startup scenario using the default ports 2551, 2552, and 0. An actor system is created on port 2551; looking at the configuration it knows that it is a seed node. The seed node actor system on port 2551 attempts to contact the actor system on port 2552, the other seed node. When the actor system on port 2552 is created it goes through the same process, in this case, 2552 attempts to contact and join with 2551. When the third actor systems is created on a random port, say port 24242, it knows from the configuration that it is not a seed node, in this case, it attempts to communicate with one of the seed actor systems, announce itself, and join the cluster.

You may have noticed that in the above example three actor systems were created in a single JVM. While it is acceptable to run multiple actor systems per JVM the more common case is to run a single actor system per JVM.

Let's look at a slightly more realistic example. Using the provided `akka` script a three node cluster is started.

~~~bash
./akka cluster start 3
~~~

Each node runs in a separate JVM. Here we have three actor systems that were started independently in three JVMs. The three actor systems followed the same startup scenario as before with the result that they formed a cluster.

Of course, the most common scenario is that each actor system is created in different JVMs each running on separate servers, virtual servers, or containers. Again, the same start up process takes place where the individual actor systems find each other across the network and form a cluster.

Let's get back to that one line of code where an actor system is created.

~~~java
ActorSystem actorSystem = ActorSystem.create("cluster", setupClusterNodeConfig(port));
~~~

From this brief description, you can see that a lot happens within the actor system abstraction layer and this summary of the startup process is just the tip of the iceberg, this is what abstraction layers are supposed to do, they hide complexity.

Once multiple actor systems join a cluster, they form a single virtual actor system from the perspective of actors running within this virtual actor system.  Of course, individual actor instances physically reside in specific cluster nodes within specific JVMs but when it comes to receiving and sending actor messages the node boundaries are transparent and virtually disappear. It is this transparency that is the foundation for building "*one application or service spans multiple nodes*."

Also, the flexibility to expand a cluster by adding more nodes is the mechinism for eliminating single points of bottlenecks. When the existing nodes in a cluster cannot handle the current load, more nodes can be added to expand the capacity. The same is true for failures. The loss of one or more nodes does not mean that the entire cluster fails. The failed node can be replaced, and actors that were running on failed nodes can be relocated to other nodes.

Hopefully, this overview has shed some light on how Akka provides "*no single point of failure or single point of bottleneck*" and how "*Akka cluster allows for building distributed applications, where one application or service spans multiple nodes.*"

### Installation

~~~bash
git clone https://github.com/mckeeh3/akka-typed-java-cluster.git
cd akka-typed-java-cluster
mvn clean package
~~~

The Maven command builds the project and creates a self contained runnable JAR.

### Run a cluster (Mac, Linux)

The project contains a set of scripts that can be used to start and stop individual cluster nodes or start and stop a cluster of nodes.

The main script `./akka` is provided to run a cluster of nodes or start and stop individual nodes.
Use `./akka node start [1-9] | stop` to start and stop individual nodes and `./akka cluster start [1-9] | stop` to start and stop a cluster of nodes.
The `cluster` and `node` start options will start Akka nodes on ports 2551 through 2559.
Both `stdin` and `stderr` output is sent to a file in the `/tmp` directory using the file naming convention `/tmp/<project-dir-name>-N.log`.

Start node 1 on port 2551 and node 2 on port 2552.
~~~bash
./akka node start 1
./akka node start 2
~~~

Stop node 3 on port 2553.
~~~bash
./akka node stop 3
~~~

Start a cluster of four nodes on ports 2551, 2552, 2553, and 2554.
~~~bash
./akka cluster start 4
~~~

Stop all currently running cluster nodes.
~~~bash
./akka cluster stop
~~~

You can use the `./akka cluster start [1-9]` script to start multiple nodes and then use `./akka node start [1-9]` and `./akka node stop [1-9]`
to start and stop individual nodes.

Use the `./akka node tail [1-9]` command to `tail -f` a log file for nodes 1 through 9.

The `./akka cluster status` command displays the status of a currently running cluster in JSON format using the
[Akka Management](https://developer.lightbend.com/docs/akka-management/current/index.html)
extension
[Cluster Http Management](https://developer.lightbend.com/docs/akka-management/current/cluster-http-management.html).

### Run a cluster (Windows, command line)

The following Maven command runs a signle JVM with 3 Akka actor systems on ports 2551, 2552, and a radmonly selected port.
~~~bash
mvn exec:java
~~~
Use CTRL-C to stop.

To run on specific ports use the following `-D` option for passing in command line arguements.
~~~bash
mvn exec:java -Dexec.args="2551"
~~~
The default no arguments is equilevalant to the following.
~~~bash
mvn exec:java -Dexec.args="2551 2552 0"
~~~
A common way to run tests is to start single JVMs in multiple command windows. This simulates running a multi-node Akka cluster.
For example, run the following 4 commands in 4 command windows.
~~~bash
mvn exec:java -Dexec.args="2551" > /tmp/$(basename $PWD)-1.log
~~~
~~~bash
mvn exec:java -Dexec.args="2552" > /tmp/$(basename $PWD)-2.log
~~~
~~~bash
mvn exec:java -Dexec.args="0" > /tmp/$(basename $PWD)-3.log
~~~
~~~bash
mvn exec:java -Dexec.args="0" > /tmp/$(basename $PWD)-4.log
~~~
This runs a 4 node Akka cluster starting 2 nodes on ports 2551 and 2552, which are the cluster seed nodes as configured and the `application.conf` file.
And 2 nodes on randomly selected port numbers.
The optional redirect `> /tmp/$(basename $PWD)-4.log` is an example for pushing the log output to filenames based on the project direcctory name.

For convenience, in a Linux command shell define the following aliases.

~~~bash
alias p1='cd ~/akka-java/akka-typed-java-cluster'
alias p2='cd ~/akka-java/akka-typed-java-cluster-aware'
alias p3='cd ~/akka-java/akka-typed-java-cluster-singleton'
alias p4='cd ~/akka-java/akka-typed-java-cluster-sharding'
alias p5='cd ~/akka-java/akka-typed-java-cluster-persistence'
alias p6='cd ~/akka-java/akka-typed-java-cluster-persistence-query'

alias m1='clear ; mvn exec:java -Dexec.args="2551" > /tmp/$(basename $PWD)-1.log'
alias m2='clear ; mvn exec:java -Dexec.args="2552" > /tmp/$(basename $PWD)-2.log'
alias m3='clear ; mvn exec:java -Dexec.args="0" > /tmp/$(basename $PWD)-3.log'
alias m4='clear ; mvn exec:java -Dexec.args="0" > /tmp/$(basename $PWD)-4.log'
~~~

The p1-6 alias commands are shortcuts for cd'ing into one of the six project directories.
The m1-4 alias commands start and Akka node with the appropriate port. Stdout is also redirected to the /tmp directory.
