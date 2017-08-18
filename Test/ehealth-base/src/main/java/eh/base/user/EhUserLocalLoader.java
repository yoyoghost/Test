package eh.base.user;

import ctd.account.UserRoleToken;
import ctd.account.user.User;
import ctd.account.user.UserRoleTokenEntity;
import ctd.controller.exception.ControllerException;
import ctd.persistence.DAOFactory;
import ctd.persistence.support.impl.user.UserLocalLoader;
import eh.base.dao.ChemistDAO;
import eh.base.dao.DoctorDAO;
import eh.base.dao.EmploymentDAO;
import eh.entity.base.Chemist;
import eh.entity.base.Doctor;
import eh.entity.base.Employment;
import eh.entity.mpi.Patient;
import eh.mpi.dao.PatientDAO;
import eh.utils.DateConversion;

import java.util.Collection;

public class EhUserLocalLoader extends UserLocalLoader {
	private String doctorRoleId = "doctor";
	private String patientRoleId = "patient";
	private String chemistRoleId = "chemist";

	protected void afterUserLoad(User user) throws ControllerException {
		Collection<UserRoleToken> urs = user.getUserRoleTokens();
		for (UserRoleToken ur : urs) {
			if (ur.getRoleId().equals(doctorRoleId)) {
				loadDoctor(ur);
			} else if (ur.getRoleId().equals(patientRoleId)) {
				loadPatient(ur);
			} else if (ur.getRoleId().equals(chemistRoleId)) {
				loadChemist(ur);
			}

		}
	}

	private void loadDoctor(UserRoleToken ur) throws ControllerException {

		Doctor d = DAOFactory.getDAO(DoctorDAO.class).getByMobile(
				(ur.getUserId()));
		if (d == null) {
			throw new ControllerException(
					ControllerException.INSTANCE_NOT_FOUND, "doctor["
							+ ur.getUserId() + "] not found.");
		}
		((UserRoleTokenEntity) ur).setProperty("doctor", d);

		/**
		 * 2015-09-02 默认获取医生第一职业
		 */
		Employment em = DAOFactory.getDAO(EmploymentDAO.class)
				.getPrimaryEmpByDoctorId(d.getDoctorId());
		if(em==null){
			throw new ControllerException(
					ControllerException.INSTANCE_NOT_FOUND, "doctor["
					+ ur.getUserId() + "].PrimaryEmployment not found.");
		}
		((UserRoleTokenEntity) ur).setProperty("employment", em);

	}

	private void loadPatient(UserRoleToken ur) throws ControllerException {

		Patient p = DAOFactory.getDAO(PatientDAO.class).getByLoginId(
				(ur.getUserId()));
		if (p == null) {
			throw new ControllerException(
					ControllerException.INSTANCE_NOT_FOUND, "patient["
							+ ur.getUserId() + "] not found.");
		}
		int age = DateConversion.getAge(p.getBirthday());
		p.setAge(age);
		UserRoleTokenEntity ure = (UserRoleTokenEntity) ur;
		ure.setProperty("patient", p);

	}

	//zhongzx
	//加载药师表信息
	private void loadChemist(UserRoleToken ur) throws ControllerException{

		Chemist chemist = DAOFactory.getDAO(ChemistDAO.class).getByLoginId(ur.getUserId());

		if(null == chemist){
			throw new ControllerException(
					ControllerException.INSTANCE_NOT_FOUND, "chemist["
					+ ur.getUserId() + "] not found.");
		}
		((UserRoleTokenEntity) ur).setProperty("chemist", chemist);

	}

	public void setDoctorRoleId(String doctorRoleId) {
		this.doctorRoleId = doctorRoleId;
	}

	public void setPatientRoleId(String patientRoleId) {
		this.patientRoleId = patientRoleId;
	}

	public void setChemistRoleId(String chemistRoleId) {this.chemistRoleId = chemistRoleId; }
}
