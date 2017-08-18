package eh.base.service;

import ctd.account.session.SessionItemManager;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.annotation.RpcService;
import eh.base.constant.ErrorCode;
import eh.bus.dao.AdviceDAO;
import eh.entity.bus.Advice;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import java.util.Date;

public class AdviceService {
    public static final Logger log = Logger.getLogger(AdviceService.class);
    /**
     * 保存反馈意见,普通反馈
     * @return
     */
    @RpcService
    public void saveAdvice(Advice advice){
        AdviceDAO dao = DAOFactory.getDAO(AdviceDAO.class);
        if(StringUtils.isEmpty(advice.getAdviceContent())){
            throw new DAOException(ErrorCode.SERVICE_ERROR,"请输入意见");
        }
        try{
            Integer deviceId = SessionItemManager.instance().checkClientAndGet();
            advice.setDeviceId(deviceId);
        }catch (Exception e){
            log.error(e.getMessage());
        }

        advice.setCreateDate(new Date());
        advice.setAdopt(0);
        advice.setAdviceBussType(0);

        dao.save(advice);
    }

    /**
     * 保存反馈意见，无药品反馈
     * @param
     */
    @RpcService
    public void saveDrugAdvice(Advice advice){
        AdviceDAO dao = DAOFactory.getDAO(AdviceDAO.class);
        if(StringUtils.isEmpty(advice.getAdviceContent())){
            throw new DAOException(ErrorCode.SERVICE_ERROR,"请输入意见");
        }
        try{
            Integer deviceId = SessionItemManager.instance().checkClientAndGet();
            advice.setDeviceId(deviceId);
        }catch (Exception e){
            log.error(e.getMessage());
        }

        advice.setCreateDate(new Date());
        advice.setAdopt(0);
        advice.setAdviceBussType(1);

        dao.save(advice);
    }

}
