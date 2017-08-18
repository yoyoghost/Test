package eh.wxpay.exception;

import ctd.persistence.exception.DAOException;

/**
 * Created by Administrator on 2017/1/6 0006.
 */
public class PriceZeroException extends DAOException {
    public PriceZeroException(String msg) {
        super(msg);
    }

    public PriceZeroException(Throwable e) {
        super(e);
    }

    public PriceZeroException(int code, Throwable e) {
        super(code, e);
    }

    public PriceZeroException(Throwable e, int code, String msg) {
        super(e, code, msg);
    }

    public PriceZeroException(int code, String msg) {
        super(code, msg);
    }
}
