package eh.mpi.dao;

import ctd.persistence.DAOFactory;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.BeanUtils;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.entity.mpi.Address;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public abstract class AddressDAO extends HibernateSupportDelegateDAO<Address> {
	private static final Log logger = LogFactory.getLog(AddressDAO.class);

	public AddressDAO() {
		super();
		this.setEntityName(Address.class.getName());
		this.setKeyField("addressId");
	}

	/**
	 * 根据addressId查询地址
	 * 
	 * @author xiebz
	 * @date 2015-12-3
	 * @param addressId
	 * @return
	 */
	@RpcService
	@DAOMethod
	public abstract Address getByAddressId(int addressId);

	/**
	 * 根据addressId删除地址
	 * 
	 * @author xiebz
	 * @date 2015-12-3
	 * @param addressId
	 */
	@DAOMethod
	public abstract void deleteByAddressId(int addressId);

	/**
	 * 删除收货地址
	 * 
	 * @author zhangx
	 * @date 2015-12-5 下午9:29:19
	 * @param addressId
	 *            收货地址ID
	 */
	@RpcService
	@SuppressWarnings("unused")
	public void delAddress(int addressId) {
		Address address = getByAddressId(addressId);
		if (address == null) {
			throw new DAOException(609, "该收货地址不存在或已删除");
		}
		logger.info("删除MPI[" + address.getMpiId() + "]的收货地址---"
				+ JSONUtils.toString(address));
		this.deleteByAddressId(addressId);
	}

	/**
	 * 增加收货地址
	 * 
	 * @author xiebz
	 * @date 2015-12-3
	 * @date 2016-3-3 luf 修改异常code
	 * @param a
	 * @return
	 */
	@RpcService
	public Integer addAddress(Address a) {
		logger.info("新增MPI[" + a.getMpiId() + "]的收货地址---"
				+ JSONUtils.toString(a));
		if (StringUtils.isEmpty(a.getMpiId())) {
			throw new DAOException(DAOException.VALUE_NEEDED,
					"mpiId is required");
		}
		PatientDAO patDao = DAOFactory.getDAO(PatientDAO.class);
		if (!patDao.exist(a.getMpiId())) {
			throw new DAOException(600, "不存在患者[" + a.getMpiId() + "]");
		}

		if (StringUtils.isEmpty(a.getReceiver())) {
			throw new DAOException(DAOException.VALUE_NEEDED,
					"receiver is required");
		}
		if (StringUtils.isEmpty(a.getRecMobile())) {
			throw new DAOException(DAOException.VALUE_NEEDED,
					"recMobile is required");
		}
		if (StringUtils.isEmpty(a.getAddress1())) {
			throw new DAOException(DAOException.VALUE_NEEDED,
					"Address1 is required");
		}
		if (StringUtils.isEmpty(a.getAddress2())) {
			throw new DAOException(DAOException.VALUE_NEEDED,
					"Address2 is required");
		}
		if (StringUtils.isEmpty(a.getAddress4())) {
			throw new DAOException(DAOException.VALUE_NEEDED,
					"Address4 is required");
		}

		Date now = new Date();
		a.setCreateDt(now);
		a.setLastModify(now);

        a = save(a);
		return a.getAddressId();
	}

	/**
	 * 修改地址
	 * 
	 * @author xiebz
	 * @date 2015-12-3
	 * @param a
	 * @return
	 */
	@RpcService
	public Integer updateAddress(final Address a) {
		logger.info("修改收货地址---" + JSONUtils.toString(a));
		if (a.getAddressId() == null) {
			throw new DAOException(DAOException.VALUE_NEEDED,
					"addressId is required");
		}
		a.setLastModify(new Date());

		Address target = getByAddressId(a.getAddressId());
		logger.info("修改前MPI[" + target.getMpiId() + "]的收货地址---"
				+ JSONUtils.toString(target));

        BeanUtils.map(a, target);
        if(StringUtils.isEmpty(a.getAddress3())){
            target.setAddress3("");
        }
        if(StringUtils.isEmpty(a.getZipCode())){
            target.setZipCode("");
        }
		Address address = update(target);

		return address.getAddressId();
	}

	/**
	 * 收货地址列表服务
	 * 
	 * @author luf
	 * @param mpiId
	 *            主索引
	 * @return List<Address>
	 */
	@RpcService
    @DAOMethod(orderBy = "lastModify desc", limit = 0)
	public abstract List<Address> findByMpiId(String mpiId);

    /**
     * 排序规则：收货人是患者本身的地址排前面，家庭成员排后面
     * @param mpiId
     * @return
     */
    @RpcService
    public List<Address> findByMpiIdOrderSelf(String mpiId){
        List<Address> newList = new ArrayList<>(0);
        if(StringUtils.isNotEmpty(mpiId)) {
            List<Address> addressList = findByMpiId(mpiId);
            PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
            String pName = patientDAO.getNameByMpiId(mpiId);
            List<Address> selfList = new ArrayList<>(0);
            List<Address> otherList = new ArrayList<>(0);
            for(Address address : addressList){
                if(pName.equals(address.getReceiver())){
                    selfList.add(address);
                }else{
                    otherList.add(address);
                }
            }

            if(CollectionUtils.isNotEmpty(selfList)){
                newList.addAll(selfList);
            }

            if(CollectionUtils.isNotEmpty(otherList)){
                newList.addAll(otherList);
            }
        }
        return newList;
    }

    /**
     * 根据患者ID获取最后修改的地址
     * @param mpiId
     * @return
     */
    public Address getLastAddressByMpiId(final String mpiId){
        HibernateStatelessResultAction<Address> action = new AbstractHibernateStatelessResultAction<Address>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String hql = new String("from Address where mpiId=:mpiId order by lastModify desc");
                Query q = ss.createQuery(hql);
                q.setParameter("mpiId", mpiId);
                q.setFirstResult(0);
                q.setMaxResults(1);
                Object obj = q.uniqueResult();
                setResult((null != obj)?(Address)obj:null);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }
}
