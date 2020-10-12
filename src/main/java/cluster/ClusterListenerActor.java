package cluster;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.StreamSupport;

import org.slf4j.Logger;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.cluster.ClusterEvent;
import akka.cluster.Member;
import akka.cluster.MemberStatus;
import akka.cluster.typed.Cluster;
import akka.cluster.typed.Subscribe;

class ClusterListenerActor extends AbstractBehavior<ClusterEvent.ClusterDomainEvent> {
  private final Cluster cluster;
  private final Logger log;
  private Nodes nodesThen;

  static Behavior<ClusterEvent.ClusterDomainEvent> create() {
    return Behaviors.setup(ClusterListenerActor::new);
  }

  private ClusterListenerActor(ActorContext<ClusterEvent.ClusterDomainEvent> context) {
    super(context);

    this.cluster = Cluster.get(context.getSystem());
    this.log = context.getLog();

    subscribeToClusterEvents();
  }

  private void subscribeToClusterEvents() {
    Cluster.get(getContext().getSystem())
        .subscriptions()
        .tell(Subscribe.create(getContext().getSelf(), ClusterEvent.ClusterDomainEvent.class));
  }

  @Override
  public Receive<ClusterEvent.ClusterDomainEvent> createReceive() {
    return newReceiveBuilder()
        .onAnyMessage(this::logClusterEvent)
        .build();
  }

  private Behavior<ClusterEvent.ClusterDomainEvent> logClusterEvent(Object clusterEventMessage) {
    log.info("{} sent to {}", clusterEventMessage, cluster.selfMember());
    logClusterMembers();

    return Behaviors.same();
  }

  private void logClusterMembers() {
    final Nodes nodesNow = Nodes.init(cluster);
    if (!nodesNow.equals(nodesThen)) {
      nodesNow.nodes.forEach(new Consumer<Node>() {
        int m = 0;

        @Override
        public void accept(Node node) {
          log.info("{} {}", ++m, node);
        }
      });
    }
    nodesThen = nodesNow;
  }

  static class Node {
    final int port;
    final String memberStatus;
    final boolean leader;
    final boolean oldest;
    final boolean unreachable;
    final Member member;

    public Node(Member member, boolean leader, boolean oldest, boolean unreachable) {
      this.port = member.address().getPort().orElse(-1);
      this.memberStatus = member.status().toString().toLowerCase();
      this.member = member;
      this.leader = leader;
      this.oldest = oldest;
      this.unreachable = unreachable;
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + (leader ? 1231 : 1237);
      result = prime * result + ((memberStatus == null) ? 0 : memberStatus.hashCode());
      result = prime * result + (oldest ? 1231 : 1237);
      result = prime * result + port;
      result = prime * result + (unreachable ? 1231 : 1237);
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj)
        return true;
      if (obj == null)
        return false;
      if (getClass() != obj.getClass())
        return false;
      Node other = (Node) obj;
      if (leader != other.leader)
        return false;
      if (memberStatus == null) {
        if (other.memberStatus != null)
          return false;
      } else if (!memberStatus.equals(other.memberStatus))
        return false;
      if (oldest != other.oldest)
        return false;
      if (port != other.port)
        return false;
      if (unreachable != other.unreachable)
        return false;
      return true;
    }

    @Override
    public String toString() {
      String msg = (leader ? ", (LEADER)" : "") + (oldest ? ", (OLDEST)" : "") + (unreachable ? ", (UNREACHABLE)" : "");
      return String.format("%s[%s, %s%s]", getClass().getSimpleName(), member.address(), memberStatus, msg);
    }
  }

  static class Nodes {
    final List<Node> nodes;

    public Nodes(List<Node> nodes) {
      this.nodes = nodes;
    }

    static Nodes init(Cluster cluster) {
      final ClusterEvent.CurrentClusterState clusterState = cluster.state();
      final Set<Member> unreachable = clusterState.getUnreachable();
      final Optional<Member> old = StreamSupport.stream(clusterState.getMembers().spliterator(), false)
          .filter(member -> member.status().equals(MemberStatus.up()))
          .filter(member -> !(unreachable.contains(member)))
          .reduce((older, member) -> older.isOlderThan(member) ? older : member);
      final Member oldest = old.orElse(cluster.selfMember());
      final List<Node> nodes = new ArrayList<>();

      StreamSupport.stream(clusterState.getMembers().spliterator(), false)
          .forEach(new Consumer<Member>() {
            @Override
            public void accept(Member member) {
              nodes.add(new Node(member, leader(member), oldest(member), unreachable(member)));
            }

            private boolean leader(Member member) {
              return member.address().equals(clusterState.getLeader());
            }

            private boolean oldest(Member member) {
              return oldest.equals(member);
            }

            private boolean unreachable(Member member) {
              return unreachable.contains(member);
            }
          });

      return new Nodes(nodes);
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((nodes == null) ? 0 : nodes.hashCode());
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj)
        return true;
      if (obj == null)
        return false;
      if (getClass() != obj.getClass())
        return false;
      Nodes other = (Nodes) obj;
      if (nodes == null) {
        if (other.nodes != null)
          return false;
      } else if (!nodes.equals(other.nodes))
        return false;
      return true;
    }

    @Override
    public String toString() {
      return String.format("%s[%s]", getClass().getSimpleName(), nodes);
    }
  }
}
