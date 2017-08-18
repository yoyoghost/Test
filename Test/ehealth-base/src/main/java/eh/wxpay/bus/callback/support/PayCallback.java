package eh.wxpay.bus.callback.support;

/**
 * 使用说明：要新加一种支付业务类型，需要实现该接口
 * 子类命名规则：BusTypeEnum的code值（首字母大写）+ PayCallback后缀
 * 子类需添加@Component注解或是其他方式显式声明为spring bean
 */
public interface PayCallback<T extends PayResult> {

    boolean handle(T t) throws Exception;


}
