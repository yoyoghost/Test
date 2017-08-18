package eh.base.user;

import ctd.account.UserRoleToken;
import eh.entity.mpi.Patient;

import java.io.Serializable;

/**
 * Created by Administrator on 2017/7/10 0010.
 */
public class RegisterResultTo implements Serializable {
    private Patient patient;
    private boolean newUser;
    private boolean couponFlag;
    private UserRoleToken urt;

    public Patient getPatient() {
        return patient;
    }

    public void setPatient(Patient patient) {
        this.patient = patient;
    }

    public boolean isNewUser() {
        return newUser;
    }

    public void setNewUser(boolean newUser) {
        this.newUser = newUser;
    }

    public boolean isCouponFlag() {
        return couponFlag;
    }

    public void setCouponFlag(boolean couponFlag) {
        this.couponFlag = couponFlag;
    }

    public UserRoleToken getUrt() {
        return urt;
    }

    public void setUrt(UserRoleToken urt) {
        this.urt = urt;
    }
}
