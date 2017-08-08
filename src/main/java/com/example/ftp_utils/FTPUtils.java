package com.example.ftp_utils;

import org.apache.commons.net.PrintCommandListener;
import org.apache.commons.net.ftp.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletResponse;
import java.io.*;

/**
 * Created by zs on 2017/8/4.
 *
 * @author zhang sen
 */
public class FTPUtils {
    private FTPClient ftpClient = new FTPClient();
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    /**
     * 设置将过程中使用到的命令输出到控制台
     */
    public FTPUtils() {
        this.ftpClient.addProtocolCommandListener(new PrintCommandListener(new PrintWriter(System.out)));
    }

    /**
     * 连接ftp服务器
     * @param hostname 主机名
     * @param port 端口
     * @param username 用户名
     * @param password 密码
     * @return 是否连接成功
     * @throws IOException
     */
    public boolean connect(String hostname, int port, String username, String password) throws IOException {
        ftpClient.connect(hostname,port);

        if (FTPReply.isPositiveCompletion(ftpClient.getReplyCode())){
            if (ftpClient.login(username,password)){
                return true;
            }
        }
        disconnect();
        return false;
    }

    /**
     * 删除FTP服务器文件
     * @param pathname 文件路径
     * @return 是否成功操作状态
     * @throws IOException
     */
    public FTPStatus delete(String pathname) throws IOException {
        ftpClient.enterLocalPassiveMode();
        ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
        FTPStatus result = null;

        FTPFile[] files = ftpClient.listFiles(pathname);
        if (files.length == 1){
            boolean status = ftpClient.deleteFile(pathname);
            result = status ? FTPStatus.Delete_Remote_Success : FTPStatus.Delete_Remote_Failed;
        } else {
            result = FTPStatus.Not_Exist_File;
        }

        logger.info("FTP服务器文件删除标识：{}", result);

        return result;
    }

    /**
     * 重命名FTP服务器文件
     * @param name 新文件名
     * @param pathname 文件路径
     * @return 是否成功操作状态
     * @throws IOException
     */
    public FTPStatus rename(String name,String pathname) throws IOException {
        ftpClient.enterLocalPassiveMode();
        ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
        FTPStatus result = null;

        FTPFile[] files = ftpClient.listFiles(pathname);
        if (files.length == 1){
            boolean status = ftpClient.rename(pathname, name);
            result = status ? FTPStatus.Remote_Rename_Success : FTPStatus.Remote_Rename_Failed;
        } else {
            result = FTPStatus.Not_Exist_File;
        }

        logger.info("FTP服务器更文件名新标识：{}", result);

        return result;
    }

    /**
     * 从FTP服务器上下载文件
     * @param pathname FTP服务器文件路径
     * @param local 本地文件路径
     * @return 是否成功操作状态
     * @throws IOException
     */
    public FTPStatus download(String pathname, String local) throws IOException {
        ftpClient.enterLocalPassiveMode();
        ftpClient.setFileType(FTP.BINARY_FILE_TYPE);

        FTPStatus result;
        File f = new File(local);
        FTPFile[] files = ftpClient.listFiles(pathname);

        if (files.length != 1){
            logger.info("远程文件不唯一");
            return FTPStatus.File_Not_Unique;
        }

        long remoteSize = files[0].getSize();

        if (f.exists()){
            OutputStream outputStream = new FileOutputStream(f, true);
            logger.info("本地文件大小为：" + f.length());

            if (f.length() >= remoteSize){
                logger.info("本地文件已存在，下载中止");
                return FTPStatus.Remote_smaller_local;
            }

            ftpClient.setRestartOffset(f.length());

            boolean status = ftpClient.retrieveFile(pathname, outputStream);
            result = status ? FTPStatus.Download_From_Break_Success : FTPStatus.Download_From_Break_Failed;
            outputStream.close();
        } else {
            OutputStream outputStream = new FileOutputStream(f);
            boolean status = ftpClient.retrieveFile(pathname, outputStream);
            result = status ? FTPStatus.Download_From_Break_Success : FTPStatus.Download_From_Break_Failed;
            outputStream.close();
        }

        return result;
    }

    /**
     * 上传文件到FTP服务器，支持断点续传
     * @param local
     * @param pathname
     * @return
     * @throws IOException
     */
    public FTPStatus upload(String local, String pathname) throws IOException {
        ftpClient.enterLocalPassiveMode();
        ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
        FTPStatus result = null;

        // 对远程目录进行处理
        String fileName = pathname;
        if (pathname.contains("/")){
            fileName = pathname.substring(pathname.lastIndexOf("/") + 1);
            String directory = pathname.substring(0, pathname.lastIndexOf("/") + 1);

            if (!directory.equalsIgnoreCase("/") && !ftpClient.changeWorkingDirectory(directory)) {
                // 如果远程目录不存在，则递归创建服务器目录
                int start = 0;
                int end = 0;

                if (directory.startsWith("/")){
                    start = 1;
                } else {
                    start = 0;
                }

                end = directory.indexOf("/",start);

                while (true){
                    String subDirectory = pathname.substring(start, end);

                    if (!ftpClient.changeWorkingDirectory(subDirectory)){
                        if (ftpClient.makeDirectory(subDirectory)){
                            ftpClient.changeWorkingDirectory(subDirectory);
                        } else {
                            logger.info("创建目录失败");
                            return FTPStatus.Create_Directory_Fail;
                        }
                    }

                    start = end + 1;
                    end = directory.indexOf("/", start);

                    // 检查所有目录是否创建完毕
                    if (end <= start){
                        break;
                    }
                }
            }
        }

        // 检查远程是否存在文件
        FTPFile[] files = ftpClient.listFiles(fileName);

        if (files.length == 1){
            long remoteSize = files[0].getSize();
            File f = new File(local);
            long localSize = f.length();

            if (remoteSize == localSize){
                return FTPStatus.File_Exits;
            } else if (remoteSize > localSize){
                return FTPStatus.Remote_Bigger_Local;
            }

            // 尝试移动文件内读取指针，实现断点续传
            InputStream inputStream = new FileInputStream(f);
            if (inputStream.skip(remoteSize) == remoteSize){
                ftpClient.setRestartOffset(remoteSize);
                if (ftpClient.storeFile(pathname, inputStream)){
                    return FTPStatus.Upload_From_Break_Success;
                }
            }

            // 如果断点续传没有成功，则删除服务器上的文件，重新上传
            if (!ftpClient.deleteFile(fileName)){
                return FTPStatus.Delete_Remote_Failed;
            }

            inputStream = new FileInputStream(f);

            if (ftpClient.storeFile(pathname, inputStream)){
                result = FTPStatus.Upload_New_File_Success;
            } else {
                result = FTPStatus.Upload_New_File_Failed;
            }

            inputStream.close();
        } else {
            InputStream inputStream = new FileInputStream(local);

            if (ftpClient.storeFile(fileName, inputStream)){
                result = FTPStatus.Upload_New_File_Success;
            } else {
                result = FTPStatus.Upload_New_File_Failed;
            }
            inputStream.close();
        }
        return result;
    }

    /**
     * 断开与FTP服务器的连接
     * @throws IOException
     */
    public void disconnect() throws IOException {
        if (ftpClient.isConnected()) {
            ftpClient.disconnect();
        }
    }

    public static void main(String[] args){
        FTPUtils myFtp = new FTPUtils();
        try {
            myFtp.connect("127.0.0.1",21,"test","test");
            // 上传
//            myFtp.upload("D:/cygz_new20170503.7z", "/cygz_new20170503.7z");
            // 重命名
//            myFtp.rename("abc.7z", "/abc");
            // 下载
            myFtp.download("/abc.7z", "j:\\local\\abc.7z");
            myFtp.disconnect();
        } catch (IOException e){
            e.printStackTrace();
        }
    }

}
