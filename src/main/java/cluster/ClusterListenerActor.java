package cluster;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.cluster.ClusterEvent;
import akka.cluster.Member;
import akka.cluster.typed.Cluster;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.Set;
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
        log.info("{} - {} sent to {}", getClass().getSimpleName(), clusterEventMessage, cluster.selfMember());
        logClusterMembers();

        return Behaviors.same();
    }

    private void logClusterMembers() {
        logClusterMembers(cluster.state());
    }

    private void logClusterMembers(ClusterEvent.CurrentClusterState currentClusterState) {
        final Optional<Member> old = StreamSupport.stream(currentClusterState.getMembers().spliterator(), false)
                .reduce((older, member) -> older.isOlderThan(member) ? older : member);

        final Member oldest = old.orElse(cluster.selfMember());
        final Set<Member> unreachable = currentClusterState.getUnreachable();
        final String className = getClass().getSimpleName();

        StreamSupport.stream(currentClusterState.getMembers().spliterator(), false)
                .forEach(new Consumer<Member>() {
                    int m = 0;

                    @Override
                    public void accept(Member member) {
                        log.info("{} - {} {}{}{}{}", className, ++m, leader(member), oldest(member), unreachable(member), member);
                    }

                    private String leader(Member member) {
                        return member.address().equals(currentClusterState.getLeader()) ? "(LEADER) " : "";
                    }

                    private String oldest(Member member) {
                        return oldest.equals(member) ? "(OLDEST) " : "";
                    }

                    private String unreachable(Member member) {
                        return unreachable.contains(member) ? "(UNREACHABLE) " : "";
                    }
                });

        currentClusterState.getUnreachable()
                .forEach(new Consumer<Member>() {
                    int m = 0;

                    @Override
                    public void accept(Member member) {
                        log.info("{} - {} {} (unreachable)", getClass().getSimpleName(), ++m, member);
                    }
                });
    }
}
