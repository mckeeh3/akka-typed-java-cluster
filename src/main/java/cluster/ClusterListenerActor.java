package cluster;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import akka.cluster.ClusterEvent;
import akka.cluster.Member;
import akka.cluster.typed.Cluster;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.StreamSupport;

class ClusterListenerActor extends AbstractBehavior<ClusterEvent.ClusterDomainEvent> {
    private final Cluster cluster;
    private final Logger log;

    static Behavior<ClusterEvent.ClusterDomainEvent> create() {
        return Behaviors.setup(ClusterListenerActor::new);
    }

    private ClusterListenerActor(ActorContext<ClusterEvent.ClusterDomainEvent> context) {
        super(context);

        this.cluster = Cluster.get(context.getSystem());
        this.log = context.getLog();
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
        logClusterMembers(cluster.state());
    }

    private void logClusterMembers(ClusterEvent.CurrentClusterState currentClusterState) {
        Optional<Member> old = StreamSupport.stream(currentClusterState.getMembers().spliterator(), false)
                .reduce((older, member) -> older.isOlderThan(member) ? older : member);

        Member oldest = old.orElse(cluster.selfMember());

        StreamSupport.stream(currentClusterState.getMembers().spliterator(), false)
                .forEach(new Consumer<Member>() {
                    int m = 0;

                    @Override
                    public void accept(Member member) {
                        log.info("{} {}{}{}", ++m, leader(member), oldest(member), member);
                    }

                    private String leader(Member member) {
                        return member.address().equals(currentClusterState.getLeader()) ? "(LEADER) " : "";
                    }

                    private String oldest(Member member) {
                        return oldest.equals(member) ? "(OLDEST) " : "";
                    }
                });

    }
}
