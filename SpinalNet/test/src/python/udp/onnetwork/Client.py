import socket
import _thread

import time

from _socket import SOL_SOCKET, SO_REUSEADDR, SO_BROADCAST

RX_IP = "127.0.0.1"
TX_IP = "255.255.255.255"
SERVER_PORT = 37984
TEXT = 'UDP LOOPBACK DATA'

received = False

sock = socket.socket(socket.AF_INET, # Internet
                     socket.SOCK_DGRAM) # UDP
sock.setsockopt(SOL_SOCKET, SO_REUSEADDR, 1)
sock.setsockopt(SOL_SOCKET, SO_BROADCAST, 1)
sock.bind((RX_IP, 0))

def rxThread(sock,dummy):
    while True:
        global received
        recv_data, addr = sock.recvfrom(2048)
        received = True
        print("received message:", recv_data, addr)
        assert recv_data.decode('UTF-8').startswith(TEXT), "received data not contain sent text"

try:
    _thread.start_new_thread(rxThread, (sock,1))
except Exception as errtxt:
    print(errtxt)

print("Send request")
sock.sendto(TEXT.encode(), (TX_IP, SERVER_PORT))
print("Wait two seconds for answers")

time.sleep(2)

assert received == True, "no data received"
print("Done")
