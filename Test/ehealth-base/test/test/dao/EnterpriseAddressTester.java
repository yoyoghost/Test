package test.dao;

import ctd.controller.exception.ControllerException;
import ctd.dictionary.DictionaryItem;
import ctd.dictionary.service.DictionaryLocalService;
import ctd.dictionary.service.DictionarySliceRecordSet;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import eh.cdr.dao.EnterpriseAddressDAO;
import eh.cdr.service.EnterpriseAddressService;
import eh.entity.cdr.EnterpriseAddress;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.ArrayList;
import java.util.List;

/**
 * 药企配送地址测试类
 * Created by zhongzixuan on 2016/6/8 0008.
 */
public class EnterpriseAddressTester extends TestCase {
    private static ClassPathXmlApplicationContext appContext;

    static{
        appContext = new ClassPathXmlApplicationContext("test/spring.xml");
    }

    /**
     * 录入药企配送地址
     * zhongzx
     */
    public void testAddAddr(){
        EnterpriseAddressDAO dao = appContext.getBean("enterpriseAddressDAO", EnterpriseAddressDAO.class);
        try{
        String[] s = new String[]{"诸暨市",
                "镇海区",
                "长兴县",
                "云和县",
                "越城区",
                "玉环县",
                "余姚市",
                "余杭区",
                "永康市",
                "永嘉县",
                "鄞州区",
                "义乌市",
                "秀洲区",
                "新城区",
                "新昌县",
                "萧山区",
                "象山县",
                "仙居县",
                "下沙区",
                "西湖区",
                "婺城区",
                "武义县",
                "吴兴区",
                "文成县",
                "温岭市",
                "桐乡市",
                "桐庐县",
                "天台县",
                "泰顺县",
                "遂昌县",
                "松阳县",
                "嵊州市",
                "嵊泗县",
                "绍兴县",
                "上虞市",
                "三门县",
                "瑞安市",
                "衢江区",
                "庆元县",
                "青田县",
                "普陀区",
                "浦江县",
                "平阳县",
                "平湖市",
                "磐安县",
                "瓯海区",
                "宁海县",
                "南浔区",
                "南湖区",
                "路桥区",
                "鹿城区",
                "龙游县",
                "龙湾区",
                "龙泉市",
                "临海市",
                "临城新区",
                "临安市",
                "莲都区",
                "乐清市",
                "兰溪市",
                "柯城区",
                "开化县",
                "景宁畲族自治县",
                "缙云县",
                "金东区",
                "椒江区",
                "江山市",
                "江东区",
                "江东高新区",
                "江北区",
                "建德市",
                "嘉兴经济开发区",
                "嘉善县",
                "黄岩区",
                "海盐县",
                "海曙区",
                "海宁市",
                "富阳市",
                "奉化市",
                "洞头县",
                "东阳市",
                "定海区",
                "德清县",
                "岱山县",
                "慈溪市",
                "淳安县",
                "常山县",
                "苍南县",
                "滨江区",
                "北仑区",
                "安吉县",
                "上城区",
                "江干区",
                "下城区",
                "拱墅区"};
           List<DictionaryItem> list = getDrugClass("",1);
            for(int i=0;i<s.length;i++){
                for(DictionaryItem d:list){
                    if(s[i].equals(d.getText())){
                        EnterpriseAddress e = new EnterpriseAddress();
                        e.setAddress(d.getKey());
                        e.setStatus(1);
                        e.setEnterpriseId(2);
                        System.out.println(JSONUtils.toString(e));
                        dao.save(e);
                    }
                }

            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    /** @author luf
    * @param parentKey
    *            父节点值
    * @param sliceType
    *            --0所有子节点 1所有叶子节点 2所有文件夹节点 3所有子级节点 4所有子级叶子节点 5所有子级文件夹节点
    * @return List<DictionaryItem>
    **/
    public List<DictionaryItem> getDrugClass(String parentKey, int sliceType) {
        DictionaryLocalService ser= AppContextHolder.getBean("dictionaryService",DictionaryLocalService.class);
        List<DictionaryItem> list = new ArrayList<DictionaryItem>();
        try {
            DictionarySliceRecordSet var = ser.getSlice(
                    "eh.base.dictionary.AddrArea", parentKey, sliceType, "",
                    0, 1000);
            list = var.getItems();

        } catch (ControllerException e) {
            e.printStackTrace();
        }
        return list;
    }


    public void testAllAddressCanSend(){
        EnterpriseAddressService service = appContext.getBean("enterpriseAddressService", EnterpriseAddressService.class);
        System.out.println(JSONUtils.toString(service.allAddressCanSend(140, "33", "3306", "330681")));
    }
}
