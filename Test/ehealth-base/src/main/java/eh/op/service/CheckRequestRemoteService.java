package eh.op.service;

import ctd.account.UserRoleToken;
import ctd.persistence.DAOFactory;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.schema.exception.ValidateException;
import ctd.util.annotation.RpcService;
import eh.bus.dao.CheckRequestDAO;
import eh.bus.dao.CheckRequestImgDAO;
import eh.entity.bus.CheckRequest;
import eh.entity.bus.CheckRequestImg;
import eh.entity.his.hisCommonModule.HisResponse;
import eh.entity.mpi.Patient;
import eh.mpi.dao.PatientDAO;
import eh.util.ChinaIDNumberUtil;
import org.apache.axis.utils.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.*;

/**
 * 远程影像诊断服务
 *
 * @author jianghc
 * @create 2016-10-26 15:11
 **/
public class CheckRequestRemoteService {
    private static final Log logger = LogFactory.getLog(CheckRequestRemoteService.class);


    /**
     * 获取远程诊断系统列表
     *
     * @param start
     * @param limit
     * @return
     */
    @RpcService
    public QueryResult<CheckRequest> queryCheckListForOP(final Integer organ, final String status, final Integer start, final Integer limit) {
        if (organ == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "organ is required!");
        }
        /*if (status == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "status is required!");
        }*/

        CheckRequestDAO checkRequestDAO = DAOFactory.getDAO(CheckRequestDAO.class);
        return checkRequestDAO.queryCheckListForRemote(organ, status, start, limit);
    }

    /**
     * 创建一个远程影像申请单
     *
     * @param checkRequest
     * @param imgs
     * @return
     */
    @RpcService
    public Map createOneRemoteRequest(CheckRequest checkRequest, List<CheckRequestImg> imgs) {
        if (checkRequest == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "checkRequest is required!");
        }
        if (imgs == null || imgs.size() <= 0) {
            throw new DAOException(DAOException.VALUE_NEEDED, "imgs is required!");
        }
        Patient patient = getPatientByCardAndName(checkRequest.getCertId(),checkRequest.getPatientName());
        CheckRequestDAO checkRequestDAO = DAOFactory.getDAO(CheckRequestDAO.class);
        CheckRequestImgDAO checkRequestImgDAO = DAOFactory.getDAO(CheckRequestImgDAO.class);
        UserRoleToken urt = UserRoleToken.getCurrent();
        checkRequest.setMpiid(patient.getMpiId());
        checkRequest.setMobile(patient.getMobile());
        checkRequest.setPatientSex(patient.getPatientSex());
        checkRequest.setRequestDoctorName(urt.getUserName());
        checkRequest.setRequestDate(new Date());
        checkRequest.setStatus(5);//已经出报告待发布
        checkRequest.setFromFlag(1);
        CheckRequest request = checkRequestDAO.save(checkRequest);
        List<CheckRequestImg> returnImgs = new ArrayList<CheckRequestImg>();
        List<String> imgCodes = new ArrayList<String>();
        for (CheckRequestImg img : imgs) {
            img.setRequestId(request.getCheckRequestId());
            img.setCreateDate(new Date());
            returnImgs.add(checkRequestImgDAO.save(img));
            imgCodes.add(img.getImgCode());
        }
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("checkRequest", request);
        map.put("imgs", returnImgs);
        request.setReportId(request.getRequestOrgan()+"r"+request.getCheckRequestId());
        checkRequestDAO.update(request);
        //调取远程申请服务
        try {
            HisResponse hisResponse = checkRequestDAO.remoteImageDiagApply(request, imgCodes);
            if (!"200".equals(hisResponse.getMsgCode())) {
                request.setStatus(1);
                //更新状态
                request.setCancelDate(new Date());
                request.setCancelResean(hisResponse.getMsg());
                request.setCancelName("系统自动取消");
                checkRequestDAO.update(request);
            }
        } catch (DAOException e) {
            request.setStatus(1);
            //更新状态
            request.setCancelDate(new Date());
            request.setCancelResean(e.getMessage());
            request.setCancelName("系统自动取消");
            checkRequestDAO.update(request);
        }
        return map;

    }

    @RpcService
    public Map getOneRemoteRequest(Integer requestId) {
        if (requestId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "requestId is required!");
        }
        CheckRequestDAO checkRequestDAO = DAOFactory.getDAO(CheckRequestDAO.class);
        CheckRequest request = checkRequestDAO.getByCheckRequestId(requestId);
        if (request == null) {
            return null;
        }
        CheckRequestImgDAO checkRequestImgDAO = DAOFactory.getDAO(CheckRequestImgDAO.class);
        List<CheckRequestImg> imgs = checkRequestImgDAO.findByRequestId(requestId);
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("checkRequest", request);
        map.put("imgs", imgs);
        return map;
    }


    @RpcService
    public void updateCancel(int appointRecordId, String cancelName,
                             String cancelResean, int status) {
        CheckRequestDAO checkRequestDAO = DAOFactory.getDAO(CheckRequestDAO.class);
        checkRequestDAO.updateCancel(appointRecordId, new Date(), cancelName, cancelResean, status);
    }

    public Patient getPatientByCardAndName( String card, String name) {
        if (card == null || StringUtils.isEmpty(card.trim())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "card id required");
        }
        if (name == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "name id required");
        }
        try {
            ChinaIDNumberUtil.convert15To18(card);
        } catch (ValidateException e) {
            throw new DAOException("身份证号不合法！");
        }
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        Patient patient = patientDAO.getByIdCard(card);
        if (patient != null) {
            if (!name.trim().equals(patient.getPatientName())) {
                throw new DAOException("该身份证号与姓名不匹配！");
            }
        } else {
            patient = new Patient();
            patient.setPatientName(name);
            patient.setIdcard(card);
            Integer sex = null;
            if (card.length() == 15) {
                sex = Integer
                        .parseInt(card.substring(card.length() - 1));
            } else {
                sex = Integer.parseInt(card.substring(
                        card.length() - 2, card.length() - 1));
            }
            patient.setPatientSex(sex % 2 == 0 ? "2" : "1");
        }
        return patient;
    }


}
