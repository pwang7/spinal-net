#! /bin/sh

set -o errexit
set -o nounset
set -o xtrace

MILL_VERSION=0.9.7

if [ ! -f mill ]; then
  curl -L https://github.com/com-lihaoyi/mill/releases/download/$MILL_VERSION/$MILL_VERSION > mill && chmod +x mill
fi

./mill version

# Check format
./mill SpinalNet.checkFormat
./mill SpinalNet.fix --check


# Run test and simulation
./mill SpinalNet.test.testOnly dma.DmaTest
./mill SpinalNet.test.testOnly sdram.SdramControllerTest
./mill SpinalNet.test.testOnly udp.UdpTest

# E2E UDP test
cd ./SpinalNet/test/src/python/udp/onnetwork
timeout 5 make &
sleep 3
python3 Client.py
