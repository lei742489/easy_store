package org.jeecgframework.boot.easy_store_boot.app.common;

public interface CommonConstant {

	/**
	 * 正常状态
	 */
	public static final Integer STATUS_NORMAL = 0;

	/**
	 * 禁用状态
	 */
	public static final Integer STATUS_DISABLE = -1;

	/**
	 * 删除标志
	 */
	public static final Integer DEL_FLAG_1 = 1;

	/**
	 * 未删除
	 */
	public static final Integer DEL_FLAG_0 = 0;

	/**
	 * 系统日志类型： 登录
	 */
	public static final int LOG_TYPE_1 = 1;
	
	/**
	 * 系统日志类型： 操作
	 */
	public static final int LOG_TYPE_2 = 2;

	/**
	 * 操作日志类型： 查询
	 */
	public static final int OPERATE_TYPE_1 = 1;
	
	/**
	 * 操作日志类型： 添加
	 */
	public static final int OPERATE_TYPE_2 = 2;
	
	/**
	 * 操作日志类型： 更新
	 */
	public static final int OPERATE_TYPE_3 = 3;
	
	/**
	 * 操作日志类型： 删除
	 */
	public static final int OPERATE_TYPE_4 = 4;
	
	/**
	 * 操作日志类型： 倒入
	 */
	public static final int OPERATE_TYPE_5 = 5;
	
	/**
	 * 操作日志类型： 导出
	 */
	public static final int OPERATE_TYPE_6 = 6;
	
	
	/** {@code 500 Server Error} (HTTP/1.0 - RFC 1945) */
    public static final Integer SC_INTERNAL_SERVER_ERROR_500 = 500;
    /** {@code 200 OK} (HTTP/1.0 - RFC 1945) */
    public static final Integer SC_OK_200 = 200;
    /**访问权限认证未通过 510*/
    public static final Integer SC_NO_AUTH=510;

	/**字典翻译文本后缀*/
	public static final String DICT_TEXT_SUFFIX = "_dictText";

	/**补全搜索最大数*/
	public static final Integer AUTO_COMPLETE_MAX_SEARCH_COUNT=50;
}
