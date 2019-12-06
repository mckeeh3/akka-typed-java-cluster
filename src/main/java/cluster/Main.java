package cluster;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.Terminated;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.cluster.ClusterEvent;
import akka.cluster.typed.Cluster;
import akka.cluster.typed.Subscribe;

class Main {
    static Behavior<Void> create() {
        return Behaviors.setup(context -> {
            bootstrap(context);

            return Behaviors.receive(Void.class).onSignal(Terminated.class, signal -> Behaviors.stopped()).build();
        });
    }

    private static void bootstrap(final ActorContext<Void> context) {
        final ActorRef<ClusterEvent.ClusterDomainEvent> clusterListener = 
            context.spawn(ClusterListenerActor.create(), "clusterListener");

        Cluster.get(context.getSystem())
            .subscriptions()
            .tell(Subscribe.create(clusterListener, ClusterEvent.ClusterDomainEvent.class));
    }
}
