package eh.op.service;

import ctd.persistence.DAOFactory;
import ctd.util.annotation.RpcService;
import eh.bus.dao.TransferDAO;
import eh.entity.bus.Transfer;
import eh.entity.bus.TransferAndPatient;
import eh.op.auth.service.SecurityService;

import java.util.*;

/**
 * Created by andywang on 2016/11/30.
 */
public class TransferOpService {
    @RpcService
    public HashMap<String, Integer> getStatisticsByInsuRecord(
            final Date startTime, final Date endTime, final Transfer tran,
            final int start, final String mpiId, final Integer type,
            final List<Integer> requestOrgans, final List<Integer> targetOrgans) {
        final TransferDAO transferDAO = DAOFactory.getDAO(TransferDAO.class);
        return transferDAO.getStatisticsByInsuRecord(startTime, endTime, tran, start, mpiId, type, requestOrgans, targetOrgans);
    }

    @RpcService
    public HashMap<String, Integer> getStatisticsByStatus(
            final Date startTime, final Date endTime, final Transfer tran,
            final int start, final String mpiId, final Integer type,
            final List<Integer> requestOrgans, final List<Integer> targetOrgans) {
        final TransferDAO transferDAO = DAOFactory.getDAO(TransferDAO.class);
        return transferDAO.getStatisticsByStatus(startTime, endTime, tran, start, mpiId, type, requestOrgans, targetOrgans);
    }

    @RpcService
    public HashMap<String, Integer> getStatisticsByRequestOrgan(
            final Date startTime, final Date endTime, final Transfer tran,
            final int start, final String mpiId, final Integer type,
            final List<Integer> requestOrgans, final List<Integer> targetOrgans) {
        final TransferDAO transferDAO = DAOFactory.getDAO(TransferDAO.class);
        return transferDAO.getStatisticsByRequestOrgan(startTime, endTime, tran, start, mpiId, type, requestOrgans, targetOrgans);
    }

    @RpcService
    public HashMap<String, Integer> getStatisticsByTargetOrgan(
            final Date startTime, final Date endTime, final Transfer tran,
            final int start, final String mpiId, final Integer type,
            final List<Integer> requestOrgans, final List<Integer> targetOrgans) {
        final TransferDAO transferDAO = DAOFactory.getDAO(TransferDAO.class);
        return transferDAO.getStatisticsByTargetOrgan(startTime, endTime, tran, start, mpiId, type, requestOrgans, targetOrgans);
    }


    /**
     * 运营平台（权限改造）
     * @param transferId
     * @return
     */
    @RpcService
    public TransferAndPatient getTransferAndCdrById(final Integer transferId) {
        TransferDAO transferDAO = DAOFactory.getDAO(TransferDAO.class);
        TransferAndPatient tap = transferDAO.getTransferAndCdrById(transferId);
        if (tap == null) {
            return null;
        }
        Set<Integer> o = new HashSet<Integer>();
        o.add(tap.getTransfer().getRequestOrgan());
        o.add(tap.getTransfer().getTargetOrgan());
        if (!SecurityService.isAuthoritiedOrgan(o)) {
            return null;
        }
        return tap;
    }

}
