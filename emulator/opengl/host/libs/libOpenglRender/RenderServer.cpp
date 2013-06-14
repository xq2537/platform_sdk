/*
* Copyright (C) 2011 The Android Open Source Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
#include "RenderServer.h"
#include "TcpStream.h"
#ifdef _WIN32
#include "Win32PipeStream.h"
#else
#include "UnixStream.h"
#endif
#include "RenderThread.h"
#include "FrameBuffer.h"
#include "osProcess.h"
#include <set>

#define STREAM_BUFFER_SIZE 4*1024*1024

typedef std::set<RenderThread *> RenderThreadsSet;

RenderServer::RenderServer() :
    m_listenSock(NULL),
    m_exiting(false)
{
}

RenderServer::~RenderServer()
{
    delete m_listenSock;
}


extern "C" int gRendererStreamMode;
extern "C" char gRendererVMIP[];

RenderServer *RenderServer::create(int port)
{
    if (gRendererStreamMode != STREAM_MODE_TCPCLI)
        return NULL;

    RenderServer *server = new RenderServer();
    if (!server) {
        return NULL;
    }

    server->m_port = port;

    return server;
}


RenderServer *RenderServer::create(char* addr, size_t addrLen)
{
    if (gRendererStreamMode == STREAM_MODE_TCPCLI)
        return NULL;

    RenderServer *server = new RenderServer();
    if (!server) {
        return NULL;
    }

    if (gRendererStreamMode == STREAM_MODE_TCP) {
        server->m_listenSock = new TcpStream();
    } else {
#ifdef _WIN32
        server->m_listenSock = new Win32PipeStream();
#else
        server->m_listenSock = new UnixStream();
#endif
    }

    char addrstr[SocketStream::MAX_ADDRSTR_LEN];
    if (server->m_listenSock->listen(addrstr) < 0) {
        ERR("RenderServer::create failed to listen\n");
        delete server;
        return NULL;
    }

    size_t len = strlen(addrstr) + 1;
    if (len > addrLen) {
        ERR("RenderServer address name too big for provided buffer: %zu > %zu\n",
                len, addrLen);
        delete server;
        return NULL;
    }
    memcpy(addr, addrstr, len);

    return server;
}

int RenderServer::Main()
{
    RenderThreadsSet threads;
    TcpStream *tcpcli_main;
    int tcpcli_data_port=0;
#define OPENGL_START_COMMAND 1001
#define OPENGL_PING 1002
#define OPENGL_PONG 1003
#define TCPCLI_NEW 1
#define TCPCLI_STOP 999
    int lasttime_ping, lasttime_pong;

restart_renderserver_main:

    if (gRendererStreamMode == STREAM_MODE_TCPCLI) {
        if (!gRendererVMIP) {
            fprintf(stderr,"VM IP is not set !\n");
            return 0;
        }
        tcpcli_main = new TcpStream(STREAM_BUFFER_SIZE);
        if (!tcpcli_main) {
            fprintf(stderr, "Failed to create TcpStream for main host connection!!!\n");
            return 0;
        }

        if (tcpcli_main->connect(gRendererVMIP, m_port) < 0) {
            fprintf(stderr, "Failed to connect to VM (TcpStream) for main host connection, IP:Port=%s:%d!!!\n", gRendererVMIP, m_port);
            delete tcpcli_main;
            osUtils::sleep(5);
            goto restart_renderserver_main;
        }

        unsigned int cmd = OPENGL_START_COMMAND;
        if (tcpcli_main->writeFully(&cmd, sizeof(cmd))) {
            fprintf(stderr, "Unable to send START OPENGL command\n");
            delete tcpcli_main;
            osUtils::sleep(5);
            goto restart_renderserver_main;
        }

        if (!tcpcli_main->readFully(&tcpcli_data_port, sizeof(tcpcli_data_port))) {
            fprintf(stderr, "Unable to read START OPENGL response\n");
            delete tcpcli_main;
            osUtils::sleep(5);
            goto restart_renderserver_main;
        }

        printf("OpenGL connected to %s:%u\n", gRendererVMIP,m_port);
        printf("Port %u will be used for OpenGL data connections\n", tcpcli_data_port);

        lasttime_ping = lasttime_pong = 0;
    }

    while(1) {
        SocketStream *stream;

        if (gRendererStreamMode != STREAM_MODE_TCPCLI) {
            stream = m_listenSock->accept();
            if (!stream) {
                fprintf(stderr,"Error accepting connection, aborting\n");
                break;
            }
        }
        else {
            unsigned int tcpcli_command;
#define PING_TIMEOUT 5

            if (!tcpcli_main->waitForDatas(PING_TIMEOUT)) {
                int ctime = time(NULL);

                if ((ctime-lasttime_ping)>PING_TIMEOUT) {
                    int cmd_ping = OPENGL_PING;
                    if ((lasttime_ping-lasttime_pong)>=PING_TIMEOUT) {
                        fprintf(stderr, "PING timed out\n");
                        delete tcpcli_main;
                        osUtils::sleep(5);
                        goto restart_renderserver_main;
                    }
                    if (tcpcli_main->writeFully(&cmd_ping, sizeof(cmd_ping))) {
                        fprintf(stderr, "Unable to write PING command\n");
                        delete tcpcli_main;
                        osUtils::sleep(5);
                        goto restart_renderserver_main;
                    }
                    lasttime_ping = ctime;
                }

                continue;
            }

            if (!tcpcli_main->readFully(&tcpcli_command, sizeof(tcpcli_command))) {
                fprintf(stderr, "Unable to read TCPCLI command\n");
                delete tcpcli_main;
                osUtils::sleep(5);
                goto restart_renderserver_main;
                break;
            }
            printf("Got command %d\n", tcpcli_command);
            switch (tcpcli_command) {
             case TCPCLI_NEW:
              {
                TcpStream *tcp_stream = new TcpStream(STREAM_BUFFER_SIZE);
                if (!tcp_stream) {

                    fprintf(stderr, "Failed to create TcpStream for host connection!!!\n");
                    break;
                }

                if (tcp_stream->connect(gRendererVMIP, tcpcli_data_port) < 0) {
                    fprintf(stderr, "Failed to connect to VM (TcpStream)!!!\n");
                    break;
                }
                stream = tcp_stream;
              }
              break;
             case TCPCLI_STOP:
              delete tcpcli_main;
              return 0;
             case OPENGL_PING: 
              tcpcli_command = OPENGL_PONG;
              if (tcpcli_main->writeFully(&tcpcli_command, sizeof(tcpcli_command))) {
                  fprintf(stderr, "Error sending PONG, closing connection\n");
                  delete tcpcli_main;
                  osUtils::sleep(5);
                  goto restart_renderserver_main;
              }
              continue;
             case OPENGL_PONG:
              lasttime_pong = 0;
              continue;
             default:
              fprintf(stderr, "Unknown TCPCLI command %d\n", tcpcli_command);
              continue;
            }
        }

        unsigned int clientFlags;
        if (!stream->readFully(&clientFlags, sizeof(unsigned int))) {
            fprintf(stderr,"Error reading clientFlags\n");
            delete stream;
            continue;
        }

        DBG("RenderServer: Got new stream!\n");

        // check if we have been requested to exit while waiting on accept
        if ((clientFlags & IOSTREAM_CLIENT_EXIT_SERVER) != 0) {
            m_exiting = true;
            break;
        }

        RenderThread *rt = RenderThread::create(stream);
        if (!rt) {
            fprintf(stderr,"Failed to create RenderThread\n");
            delete stream;
        }

        if (!rt->start()) {
            fprintf(stderr,"Failed to start RenderThread\n");
            delete stream;
            delete rt;
        }

        //
        // remove from the threads list threads which are
        // no longer running
        //
        for (RenderThreadsSet::iterator n,t = threads.begin();
             t != threads.end();
             t = n) {
            // first find next iterator
            n = t;
            n++;

            // delete and erase the current iterator
            // if thread is no longer running
            if ((*t)->isFinished()) {
                delete (*t);
                threads.erase(t);
            }
        }

        // insert the added thread to the list
        threads.insert(rt);

        DBG("Started new RenderThread\n");
    }

    //
    // Wait for all threads to finish
    //
    for (RenderThreadsSet::iterator t = threads.begin();
         t != threads.end();
         t++) {
        int exitStatus;
        (*t)->wait(&exitStatus);
        delete (*t);
    }
    threads.clear();

    //
    // de-initialize the FrameBuffer object
    //
    FrameBuffer::finalize();
    return 0;
}
