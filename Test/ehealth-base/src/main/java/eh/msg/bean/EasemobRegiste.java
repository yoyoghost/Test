package eh.msg.bean;

import static eh.msg.bean.EasemobBusType.REGISTE;

/**
 * Created by shenhj on 2017/7/10.
 */
public class EasemobRegiste extends EasemobBase {

    private String userName;
    private String password;

    public EasemobRegiste(String userName, String password) {
        super(REGISTE);
        this.userName = userName;
        this.password = password;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public String toString() {
        return "EasemobRegiste{" +
                "userName='" + userName + '\'' +
                ", password='" + password + '\'' +
                '}';
    }
}
