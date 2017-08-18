package eh.base.service.cdr;

import ctd.persistence.DAOFactory;
import ctd.util.annotation.RpcService;
import eh.base.cdr.api.service.CdrBaseService;
import eh.base.dao.*;
import eh.entity.base.Department;
import eh.entity.base.Doctor;
import eh.entity.base.DrugList;
import eh.entity.base.Employment;

import java.util.List;

/**
 * base向cdr提供的服务
 * Created by jiangtingfeng on 2017/7/20.
 */
public class CdrBaseServiceImpl implements CdrBaseService
{
    private AuditPrescriptionOrganDAO aduDao =
        DAOFactory.getDAO(AuditPrescriptionOrganDAO.class);

    private DepartmentDAO depDao = DAOFactory.getDAO(DepartmentDAO.class);

    private DoctorDAO docDao = DAOFactory.getDAO(DoctorDAO.class);

    private DrugListDAO druDao = DAOFactory.getDAO(DrugListDAO.class);

    private EmploymentDAO empDao = DAOFactory.getDAO(EmploymentDAO.class);

    private HisServiceConfigDAO hisDao = DAOFactory.getDAO(HisServiceConfigDAO.class);

    private OrganConfigDAO orgConfigDao = DAOFactory.getDAO(OrganConfigDAO.class);

    private OrganDAO orgDao = DAOFactory.getDAO(OrganDAO.class);

    private OrganDrugListDAO orgDrugListDao = DAOFactory.getDAO(OrganDrugListDAO.class);

    private RelationLabelDAO relDao = DAOFactory.getDAO(RelationLabelDAO.class);

    private RelationPatientDAO relPatientDao =
        DAOFactory.getDAO(RelationPatientDAO.class);

    private SaleDrugListDAO salDao = DAOFactory.getDAO(SaleDrugListDAO.class);



    public List<Integer> findOrganIdsByDoctorId(int doctorId)
    {
        return aduDao.findOrganIdsByDoctorId(doctorId);
    }

    public Department getDepartmentById(Integer deptId)
    {
        return depDao.getById(deptId);
    }

    @RpcService
    public Doctor getByMobile(String mobile)
    {
        return docDao.getByMobile(mobile);
    }

    @RpcService
    public Doctor getByDoctorId(int doctorId)
    {
        return docDao.getByDoctorId(doctorId);
    }

    @RpcService
    public Doctor get(int doctorId)
    {
        return docDao.get(doctorId);
    }

    @RpcService
    public DrugList getDrugListById(int drugId)
    {
        return druDao.getById(drugId);
    }

    @RpcService
    public List<DrugList> findAll()
    {
        return druDao.findAll();
    }

    @RpcService
    public DrugList get(Integer drugId)
    {
        return druDao.get(drugId);
    }

    @RpcService
    public Employment getByJobNumberAndOrganId(String jobNumber,int organId)
    {
        return empDao.getByJobNumberAndOrganId(jobNumber,organId);
    }

    @RpcService
    public Employment getPrimaryEmpByDoctorId(Integer doctorId)
    {
        return empDao.getPrimaryEmpByDoctorId(doctorId);
    }

    @RpcService
    public String getJobNumberByDoctorIdAndOrganIdAndDepartment(int doctorId,int organId,Integer department)
    {
        return empDao.getJobNumberByDoctorIdAndOrganIdAndDepartment(doctorId,organId,department);
    }
}
