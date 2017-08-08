package com.example.ftp_utils;

/**
 * Created by zs on 2017/8/4.
 * FTP文件操作状态枚举类
 * @author zhang sen
 */
public enum FTPStatus {
    File_Exits(0),
    Create_Directory_Success(1),
    Create_Directory_Fail(2),
    Upload_From_Break_Success(3),
    Upload_From_Break_Failed(4),
    Download_From_Break_Success(5),
    Download_From_Break_Failed(6),
    Upload_New_File_Success(7),
    Upload_New_File_Failed(8),
    Delete_Remote_Success(9),
    Delete_Remote_Failed(10),
    Remote_Bigger_Local(11),
    Remote_smaller_local(12),
    Not_Exist_File(13),
    Remote_Rename_Success(14),
    Remote_Rename_Failed(15),
    File_Not_Unique(16);

    private int status;

    public int getStatus()
    {
        return status;
    }

    public void setStatus(int status)
    {
        this.status = status;
    }

    FTPStatus(int status)
    {
        this.status = status;
    }
}
