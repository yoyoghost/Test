package eh.base.dao;

import ctd.account.UserRoleToken;
import ctd.persistence.DAOFactory;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.impl.dictionary.DBDictionaryItemLoader;
import ctd.util.annotation.RpcService;
import ctd.util.context.Context;
import ctd.util.context.ContextUtils;
import eh.entity.base.Employment;
import eh.entity.base.Organ;
import eh.entity.base.UnitOpauthorize;
import eh.entity.base.UnitOpauthorizeAndOrgan;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public abstract class UnitOpauthorizeDAO extends
		HibernateSupportDelegateDAO<UnitOpauthorize> implements
		DBDictionaryItemLoader<UnitOpauthorize> {

	public UnitOpauthorizeDAO() {
		super();
		this.setEntityName(UnitOpauthorize.class.getName());
		this.setKeyField("uauthorizeId");
	}

	@RpcService
	@DAOMethod
	public abstract List<UnitOpauthorize> findByOrganIdAndAccreditOrgan(
			Integer organId, Integer accreditOrgan);

	/**
	 * 删除授权
	 *
	 * @author LF
	 * @param uauthorizeId
	 */
	@RpcService
	@DAOMethod
	public abstract void deleteUnitOpauthorizeByUauthorizeId(
			Integer uauthorizeId);

	/**
	 * 增加授权
	 *
	 * @author LF
	 * @param unitOpauthorize
	 * @return
	 */
	@RpcService
	public UnitOpauthorize addUnitOpauthorize(UnitOpauthorize unitOpauthorize) {
		if (unitOpauthorize.getOrganId() == null) {
			throw new DAOException(DAOException.VALUE_NEEDED,
					"organId is required!");
		}
		if (unitOpauthorize.getAccreditOrgan() == null) {
			throw new DAOException(DAOException.VALUE_NEEDED,
					"accreditOrgan is required!");
		}
		if (StringUtils.isEmpty(unitOpauthorize.getAccreditBuess())) {
			throw new DAOException(DAOException.VALUE_NEEDED,
					"accreditBuess is required!");
		}
		UnitOpauthorize uOpauthorize = new UnitOpauthorize();
		if (unitOpauthorize.getOrderNum() == null) {
			unitOpauthorize.setOrderNum(0);
		}
		uOpauthorize = save(unitOpauthorize);
		return uOpauthorize;
	}

	/**
	 * 查询机构授权记录 授权机构（供查询机构授权记录调用）
	 *
	 * @author LF
	 * @param organId
	 * @param start
	 * @return
	 */
	@DAOMethod(limit = 10)
	public abstract List<UnitOpauthorize> findByOrganId(Integer organId,
			long start);

	/**
	 * 查询机构授权记录（授权机构）
	 *
	 * @author LF
	 * @param organId
	 * @param start
	 * @return
	 */
	@RpcService
	public List<UnitOpauthorizeAndOrgan> findUnitOpauthorizeAndOrgans(
			Integer organId, long start) {
		List<UnitOpauthorize> unitOpauthorizes = findByOrganId(organId, start);
		List<UnitOpauthorizeAndOrgan> opauthorizeAndOrgans = new ArrayList<UnitOpauthorizeAndOrgan>();
		OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
		UnitOpauthorizeAndOrgan unitOpauthorizeAndOrgan1 = new UnitOpauthorizeAndOrgan();
		Organ organ = new Organ();
		organ = organDAO.getByOrganId(organId);
		unitOpauthorizeAndOrgan1.setOrgan(organ);
		opauthorizeAndOrgans.add(unitOpauthorizeAndOrgan1);
		for (int i = 0; i < unitOpauthorizes.size(); i++) {
			Organ accreditOrgan = new Organ();
			accreditOrgan = organDAO.getByOrganId(unitOpauthorizes.get(i)
					.getAccreditOrgan());
			UnitOpauthorizeAndOrgan unitOpauthorizeAndOrgan = new UnitOpauthorizeAndOrgan();
			unitOpauthorizeAndOrgan.setUnitOpauthorize(unitOpauthorizes.get(i));
			unitOpauthorizeAndOrgan.setAccreditOrgan(accreditOrgan);
			opauthorizeAndOrgans.add(unitOpauthorizeAndOrgan);
		}
		return opauthorizeAndOrgans;
	}

	/**
	 * 供查询机构被授权记录调用
	 *
	 * @author LF
	 * @param accreditOrgan
	 * @param start
	 * @return
	 */
	@RpcService
	@DAOMethod(limit = 10,orderBy="orderNum")
	public abstract List<UnitOpauthorize> findByAccreditOrgan(
			Integer accreditOrgan, long start);

	/**
	 * 供查询机构被授权记录调用
	 *
	 * @author LF
	 * @param accreditOrgan
	 * @param start
	 * @return
	 */
	@RpcService
	@DAOMethod(sql = "From UnitOpauthorize where accreditOrgan=:accreditOrgan and accreditBuess=1")
	public abstract List<UnitOpauthorize> findListByAccreditOrgan(@DAOParam("accreditOrgan") Integer accreditOrgan);

    @DAOMethod(sql = "select organId From UnitOpauthorize where accreditOrgan=:accreditOrgan and accreditBuess=1",limit = 9999)
    public abstract List<Integer> findByAccreditOrgan(@DAOParam("accreditOrgan") Integer accreditOrgan);

	/**
	 * 查询机构被授权记录（被授权机构）
	 *
	 * @author LF
	 * @param accreditOrganId
	 * @param start
	 * @return
	 */
	@RpcService
	public List<UnitOpauthorizeAndOrgan> findUnitOpauthorizeAndAccreditOrgans(
			Integer accreditOrganId, long start) {
		List<UnitOpauthorize> unitOpauthorizes = findByAccreditOrgan(
				accreditOrganId, start);
		List<UnitOpauthorizeAndOrgan> opauthorizeAndOrgans = new ArrayList<UnitOpauthorizeAndOrgan>();
		OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
		UnitOpauthorizeAndOrgan unitOpauthorizeAndOrgan1 = new UnitOpauthorizeAndOrgan();
		Organ accreditOrgan = new Organ();
		accreditOrgan = organDAO.getByOrganId(accreditOrganId);
		unitOpauthorizeAndOrgan1.setAccreditOrgan(accreditOrgan);
		opauthorizeAndOrgans.add(unitOpauthorizeAndOrgan1);
		for (int i = 0; i < unitOpauthorizes.size(); i++) {
			Organ organ = new Organ();
			organ = organDAO.getByOrganId(unitOpauthorizes.get(i).getOrganId());
			UnitOpauthorizeAndOrgan unitOpauthorizeAndOrgan = new UnitOpauthorizeAndOrgan();
			unitOpauthorizeAndOrgan.setUnitOpauthorize(unitOpauthorizes.get(i));
			unitOpauthorizeAndOrgan.setOrgan(organ);
			opauthorizeAndOrgans.add(unitOpauthorizeAndOrgan);
		}
		return opauthorizeAndOrgans;
	}

	/**
	 * 根据授权机构和被授权机构和业务类型查授权纪录（供 授权记录查询服务调用）
	 *
	 * @author LF
	 * @param organId
	 * @param accreditOrgan
	 * @param accreditBuess
	 * @return
	 */
	@RpcService
	@DAOMethod(sql = "From UnitOpauthorize where organId=:organId and accreditOrgan=:accreditOrgan and accreditBuess=:accreditBuess")
	public abstract List<UnitOpauthorize> findByOrgAndAccAndBus(
			@DAOParam("organId") Integer organId,
			@DAOParam("accreditOrgan") Integer accreditOrgan,
			@DAOParam("accreditBuess") String accreditBuess);

	/**
	 *
	 * Title: 授权记录查询服务()
	 *
	 * @author AngryKitty
	 * @date 2015-9-15
	 * @param bus
	 *            --业务类型
	 * @return List<Integer> --授权及被授权机构内码列表</br>
	 *
	 *         2015-10-23 zhangx 授权机构由于前段排序显示问题，对原来的设计进行修改</br>
	 *         修改前：A授权给B，B能看到A，A也能看到B;修改后：A授权给B，B能看到A，A不能看到B</br>
	 *         增加按orderNum排序</br>
	 */
	@RpcService
	public List<Integer> findByBussId(int bus) {
        UserRoleToken ur = (UserRoleToken) ContextUtils
                .get(Context.USER_ROLE_TOKEN);
        Employment employment = (Employment) ur.getProperty("employment");
        if (employment.getOrganId() == null) {
            return null;
        }
        Integer requestOrgan = employment.getOrganId();
        String bussType = "0";
        switch (bus) {
            case 1:
            case 4:
                bussType = "1";
                break;
            case 2:
                bussType = "2";
                break;
            case 3:
            default:
                return null;
        }
        List<UnitOpauthorize> opauthorizes = this.findByAccAndBus(requestOrgan,
                bussType);
        // List<UnitOpauthorize> opauthorizes2 = this.findByOrgAndBus(
        // requestOrgan, bussType);
        List<Integer> organs = new ArrayList<Integer>();
        for (UnitOpauthorize a : opauthorizes) {
            Integer organId = a.getOrganId();
            if (!organs.contains(organId)) {
                organs.add(organId);
            }
        }
        // for(UnitOpauthorize o:opauthorizes2) {
        // Integer accreditOrgan = o.getAccreditOrgan();
        // if(!organs.contains(accreditOrgan)) {
        // organs.add(accreditOrgan);
        // }
        // }
        //app3.4需求：若没有授权机构，则查询所有有效医院
        if (null == organs || organs.isEmpty()) {
            OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
            organs = organDAO.findEffOrganWithoutOthor();
        }
        return organs;
    }

	/**
	 *
	 * Title: 根据授权机构和被授权机构和业务类型查授权纪录（供 授权记录查询服务调用） Description:
	 *
	 * @author AngryKitty
	 * @date 2015-9-15
	 * @param accreditOrgan
	 *            --申请机构ID
	 * @param accreditBuess
	 *            --授权业务类型
	 * @return List<UnitOpauthorize>
	 */
	@RpcService
	@DAOMethod(sql = "From UnitOpauthorize where accreditOrgan=:accreditOrgan and accreditBuess=:accreditBuess order by orderNum")
	public abstract List<UnitOpauthorize> findByAccAndBus(
			@DAOParam("accreditOrgan") Integer accreditOrgan,
			@DAOParam("accreditBuess") String accreditBuess);

	/**
	 * 根据机构内码和业务类型查询授权机构列表（供 授权记录查询服务调用）
	 *
	 * @author luf
	 * @param organId
	 *            机构代码（被授权）
	 * @param accreditBuess
	 *            授权业务--1、转诊 2、会诊
	 * @return List<UnitOpauthorize>
	 */
	@RpcService
	@DAOMethod(sql = "From UnitOpauthorize "
			+ "where organId=:organId and accreditBuess=:accreditBuess")
	public abstract List<UnitOpauthorize> findByOrgAndBus(
			@DAOParam("organId") Integer organId,
			@DAOParam("accreditBuess") String accreditBuess);

	/**
	 * 更新授权机构
	 *
	 * @author luf
	 * @param unitOpauthorize
	 *            授权机构信息
	 * @throws DAOException
	 */
	@RpcService
	public void updateUnitOpauthorizeWithoutThree(
			UnitOpauthorize unitOpauthorize) throws DAOException {
		if (unitOpauthorize == null) {
			throw new DAOException(DAOException.VALUE_NEEDED,
					"unitOpauthorize is required!");
		}
		Integer uauthorizeId = unitOpauthorize.getUauthorizeId();
		if (uauthorizeId == null) {
			throw new DAOException(DAOException.VALUE_NEEDED,
					"uauthorizeId is required!");
		}
		UnitOpauthorize u = this.get(uauthorizeId);
		unitOpauthorize.setOrganId(u.getOrganId());
		unitOpauthorize.setAccreditOrgan(u.getAccreditOrgan());
		unitOpauthorize.setAccreditBuess(u.getAccreditBuess());
		this.update(unitOpauthorize);
	}

    /**
     * 删除指定机构的所有授权[运营平台调用]
     *
     * @param organId
     * @author
     */
    @RpcService
    @DAOMethod
    public abstract void deleteUnitOpauthorizeByOrganId(Integer organId);
}
