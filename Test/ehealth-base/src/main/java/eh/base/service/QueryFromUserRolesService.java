package eh.base.service;

import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.annotation.RpcService;
import eh.base.dao.DoctorDAO;
import eh.base.dao.EmploymentDAO;
import eh.base.dao.UserRolesDAO;
import eh.entity.base.Doctor;
import eh.entity.base.Employment;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by yuanb on 2017/2/16.
 * 从UserRoles中查询相关信息
 */

public class QueryFromUserRolesService {
    public static final Logger log = Logger.getLogger(QueryFromUserRolesService.class);

    @RpcService
    public List<Integer> findDoctorIdsByUrtIds(List<Integer> UrtIds) {
        if (UrtIds == null || UrtIds.isEmpty()) {
            log.error("findDoctorIdsByUrtIds的入参UrtIds为空");
            return new ArrayList<>();
        }
        UserRolesDAO userRolesDAO = DAOFactory.getDAO(UserRolesDAO.class);
        List<Integer> docIds = userRolesDAO.findDoctorListByUrtIdList(UrtIds);
        if (docIds.isEmpty()) {
            log.error("UserRoles表中不存在传入的UrtIds对应的医生");
            return new ArrayList<>();
        }
        return docIds;
    }

    @RpcService
    public Doctor findDoctorByUrtId(Integer urtId) {
        if (urtId == null || urtId <= 0) {
            throw new DAOException(DAOException.VALUE_NEEDED, "urtId is required!");
        }
        List<Integer> urtIds = new ArrayList<Integer>();
        urtIds.add(urtId);
        UserRolesDAO userRolesDAO = DAOFactory.getDAO(UserRolesDAO.class);
        List<Integer> docIds = userRolesDAO.findDoctorListByUrtIdList(urtIds);
        if (docIds.isEmpty()) {
            throw new DAOException(DAOException.VALUE_NEEDED, "docIds is required!");
        }
        Integer doctorId = docIds.get(0);
        if (doctorId == null || doctorId <= 0) {
            throw new DAOException(DAOException.VALUE_NEEDED, "doctorId is required!");
        }
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        Doctor doctor = doctorDAO.get(doctorId);
        if (doctor != null) {
            EmploymentDAO employmentDAO = DAOFactory.getDAO(EmploymentDAO.class);
            Employment employment = employmentDAO
                    .getPrimaryEmpByDoctorId(doctorId);
            if (employment != null) {
                doctor.setDepartment(employment.getDepartment());
            }
        }
        return doctor;
    }

}
