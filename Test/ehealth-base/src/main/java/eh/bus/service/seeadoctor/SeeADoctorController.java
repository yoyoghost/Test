package eh.bus.service.seeadoctor;

import com.google.common.cache.CacheBuilder;
import ctd.controller.support.AbstractController;
import eh.entity.bus.seeadoctor.SeeADoctorOrgan;

import java.util.concurrent.TimeUnit;

/**
 * Created by Administrator on 2016/8/4 0004.
 */
public class SeeADoctorController extends AbstractController<SeeADoctorOrgan> {
    private static SeeADoctorController instance;
    private static final int EXPIRES_TIME = 24;

    public SeeADoctorController() {
        if(instance != null) {
            this.setInitList(instance.getCachedList());
        }
        instance = this;
    }

    protected void createCacheStore() {
        this.store = CacheBuilder.newBuilder().expireAfterAccess(EXPIRES_TIME, TimeUnit.HOURS).build(this.createCacheLoader());
    }

    public static SeeADoctorController instance() {
        if(instance == null) {
            instance = new SeeADoctorController();
        }
        return instance;
    }
}
