package eh.op.service;

import ctd.account.UserRoleToken;
import ctd.persistence.DAOFactory;
import ctd.util.annotation.RpcService;
import eh.base.service.BusActionLogService;
import eh.entity.bus.InternalSystemMessage;
import eh.op.dao.InternalSystemMessageDAO;
import eh.utils.DateConversion;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.Date;
import java.util.List;

/**
 * Created by andywang on 2017/6/19.
 */
public class InternalSystemMessageOpService {

    private static final Log logger = LogFactory.getLog(InternalSystemMessageOpService.class);

    //获取可见的系统警告消息列表
    @RpcService
    public List<InternalSystemMessage> findActivePlatformUrgentMessage(){
        InternalSystemMessageDAO imd = DAOFactory.getDAO(InternalSystemMessageDAO.class);
        String manageUnit = UserRoleToken.getCurrent().getManageUnit();
        if ("eh".equals(manageUnit) )
        {
            return imd.findActivePlatformUrgentMessage();
        }
        return null;
    }

    //获取隐藏的系统警告消息列表
    @RpcService
    public List<InternalSystemMessage> findHiddenPlatformUrgentMessage(){
        InternalSystemMessageDAO imd = DAOFactory.getDAO(InternalSystemMessageDAO.class);
        String manageUnit = UserRoleToken.getCurrent().getManageUnit();
        if ("eh".equals(manageUnit) )
        {
            return imd.findHiddenPlatformUrgentMessage();
        }
        return null;
    }

    //根据ID隐藏消息
    @RpcService
    public void hideMessageById(final Integer id) {
        InternalSystemMessageDAO imd = DAOFactory.getDAO(InternalSystemMessageDAO.class);
        InternalSystemMessage msg = imd.get(id);
        if (msg != null){
            String manageUnit = UserRoleToken.getCurrent().getManageUnit();
            //非平台管理员不能隐藏平台的message
            if (!"eh".equals(manageUnit) && msg.getVisibleTo() == -1){
                String message = manageUnit + " 隐藏系统消息失败，权限不允许。  " + msg.getTitle() + "(" + id + ")" + " 发生时间： " + DateConversion.formatDateTime(msg.getCreateTime());
                BusActionLogService.recordBusinessLog("告警消息", id.toString(), "InternalSystemMessage", message);
                logger.error(message);
                return ;
            }
            msg = imd.hideMessageById(id);
            if (msg !=null){
                String message = "解决告警问题： " + msg.getTitle() + "(" + id + ")" + " 发生时间： " + DateConversion.formatDateTime(msg.getCreateTime());
                BusActionLogService.recordBusinessLog("告警消息", id.toString(), "InternalSystemMessage", message);
            }
        }
    }

    //根据ID判断消息是否可见
    @RpcService
    public Boolean checkActiveStatusById(Integer id){
        InternalSystemMessageDAO imd = DAOFactory.getDAO(InternalSystemMessageDAO.class);
        return imd.checkActiveStatusById(id);
    }

    @RpcService
    public Integer addMessage(Integer visibleTo, Integer emergency, String title, String message, Date startTime, Date endTime){
        InternalSystemMessageDAO imd = DAOFactory.getDAO(InternalSystemMessageDAO.class);
        return imd.addMessage(visibleTo,emergency,title,message,startTime,endTime);
    }


}
