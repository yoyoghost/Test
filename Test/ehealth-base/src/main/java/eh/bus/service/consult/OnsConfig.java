package eh.bus.service.consult;

/**
 * Created by Administrator on 2016/6/13 0013.
 */
public class OnsConfig {
    public static boolean onsSwitch;
    public static String patientTopic;
    public static String doctorTopic;
    public static String sensitiveTopic;
    public static String pushTopic;
    public static String couponTopic;
    public static String logTopic;
    public static String accountTopic;
    public static String easemobTopic;

    public String getSensitiveTopic() {
        return sensitiveTopic;
    }

    public void setSensitiveTopic(String sensitiveTopic) {
        this.sensitiveTopic = sensitiveTopic;
    }

    public String getDoctorTopic() {
        return doctorTopic;
    }

    public void setDoctorTopic(String doctorTopic) {
        this.doctorTopic = doctorTopic;
    }

    public String getPatientTopic() {
        return patientTopic;
    }

    public void setPatientTopic(String patientTopic) {
        this.patientTopic = patientTopic;
    }

    public boolean isOnsSwitch() {
        return onsSwitch;
    }

    public void setOnsSwitch(boolean onsSwitch) {
        this.onsSwitch = onsSwitch;
    }

    public String getPushTopic() {
        return pushTopic;
    }

    public void setPushTopic(String pushTopic) {
        OnsConfig.pushTopic = pushTopic;
    }

    public String getCouponTopic() {
        return couponTopic;
    }

    public void setCouponTopic(String couponTopic) {
        OnsConfig.couponTopic = couponTopic;
    }

    public String getLogTopic() {
        return logTopic;
    }

    public void setLogTopic(String logTopic) {
        OnsConfig.logTopic = logTopic;
    }

    public static String getAccountTopic() {
        return accountTopic;
    }

    public static void setAccountTopic(String accountTopic) {
        OnsConfig.accountTopic = accountTopic;
    }

    public String getEasemobTopic() {
        return easemobTopic;
    }

    public void setEasemobTopic(String easemobTopic) {
        OnsConfig.easemobTopic = easemobTopic;
    }
}
