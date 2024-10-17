package com.wizarpos.aidl;

interface ICloudPay{
	String getPOSInfo(String jsonData);
	String transact(String jsonData);
	String settle(String jsonData);
	String printLast(String jsonData);
	String login();
	String setParams(String jsonData); //support change IP/PORT/TPDU/Nii....
	void cancelTransaction();
}