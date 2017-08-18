package eh.bus.service.seeadoctor;

import ctd.controller.watcher.ConfigurableWatcher;

/**
 * Created by Administrator on 2016/8/4 0004.
 */
public class SeeADoctorWatcher extends ConfigurableWatcher {

    public SeeADoctorWatcher() {
        super(SeeADoctorService.class.getSimpleName());
    }
}
