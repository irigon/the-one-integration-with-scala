package routing;

public class CompartmentSwitcher {
    final ScheduledCompartment scheduledCompartment = new ScheduledCompartment();
    final PredictiveCompartment predictiveCompartment = new PredictiveCompartment();
    final NaiveCompartment naiveCompartment = new NaiveCompartment();

    public CompartmentSwitcher() {
          scheduledCompartment.reconfigure(true, false);
          predictiveCompartment.reconfigure(true, false);
          naiveCompartment.reconfigure(true, false);
    }

    public AbstractCompartment activate(ActiveRouter ar, String ctxt) {
        switch (ctxt){
            case "scheduled_ctxt" :
                return scheduledCompartment;
            case "predictive_ctxt" :
                return predictiveCompartment;
            default :
                return naiveCompartment;
        }
    }
}
