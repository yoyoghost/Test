package eh.bus.service.seeadoctor;

import ctd.controller.ConfigurableLoader;
import ctd.controller.exception.ControllerException;
import ctd.persistence.DAOFactory;
import eh.base.dao.HisServiceConfigDAO;
import eh.base.dao.OrganDAO;
import eh.entity.base.HisServiceConfig;
import eh.entity.base.Organ;
import eh.entity.bus.seeadoctor.SeeADoctorOrgan;
import eh.utils.ValidateUtil;

/**
 * Created by Administrator on 2016/8/4 0004.
 */
public class SeeADoctorLoader implements ConfigurableLoader<SeeADoctorOrgan> {

    @Override
    public SeeADoctorOrgan load(String organIdString) throws ControllerException {
        if(ValidateUtil.blankString(organIdString)){
            return null;
        }
        try {
            Integer organId = Integer.valueOf(organIdString);
            Organ organ = DAOFactory.getDAO(OrganDAO.class).getByOrganId(organId);
            HisServiceConfig organConfig = DAOFactory.getDAO(HisServiceConfigDAO.class).getByOrganId(organId);
            SeeADoctorOrgan sadOrgan = new SeeADoctorOrgan();
            sadOrgan.setOrgan(organ);
            sadOrgan.setOrganId(organId);
            sadOrgan.setOrganConfig(organConfig);
            sadOrgan.setConnectHisCallNumSystem(ValidateUtil.notNullAndZeroInteger(organConfig.getCallNum()));
            return sadOrgan;
        }catch (Exception e){
            throw new ControllerException(e);
        }
    }
}
