package eh.op.service;

import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.annotation.RpcService;
import eh.bus.dao.ConsultDAO;
import eh.entity.bus.Consult;

import java.util.Date;
import java.util.HashMap;
import java.util.List;

/**
 * Created by andywang on 2016/11/29.
 */
public class ConsultOpService {

    @RpcService
    public HashMap<String, Integer> getStatisticsByConsultOrgan(
            final Date startTime, final Date endTime, final Consult consult,
            final int start, final List<Integer> consultOrgans,
            final String mpiid) {
        if (startTime == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "统计开始时间不能为空");
        }
        if (endTime == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "统计结束时间不能为空");
        }
        ConsultDAO consultDAO = DAOFactory.getDAO(ConsultDAO.class);
        return consultDAO.getStatisticsByConsultOrgan(startTime, endTime, consult, start, consultOrgans, mpiid);
    }

    @RpcService
    public HashMap<String, Integer> getStatisticsByRequestMode(
            final Date startTime, final Date endTime, final Consult consult,
            final int start, final List<Integer> consultOrgans,
            final String mpiid) {
        if (startTime == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "统计开始时间不能为空");
        }
        if (endTime == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "统计结束时间不能为空");
        }
        ConsultDAO consultDAO = DAOFactory.getDAO(ConsultDAO.class);
        return consultDAO.getStatisticsByRequestMode(startTime, endTime, consult, start, consultOrgans, mpiid);
    }

    @RpcService
    public HashMap<String, Integer> getStatisticsByStatus(
            final Date startTime, final Date endTime, final Consult consult,
            final int start, final List<Integer> consultOrgans,
            final String mpiid) {
        if (startTime == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "统计开始时间不能为空");
        }
        if (endTime == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "统计结束时间不能为空");
        }
        ConsultDAO consultDAO = DAOFactory.getDAO(ConsultDAO.class);
        return consultDAO.getStatisticsByStatus(startTime, endTime, consult, start, consultOrgans, mpiid);
    }
}
