FROM ubuntu:18.04

RUN apt-get update && apt-get install -y openssh-server \
    openjdk-11-jre-headless \
    default-jdk \
    tmux \
    git
RUN mkdir /var/run/sshd
RUN mkdir /root/.ssh
RUN echo "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQCyd4DY0hlBTdJFi2kWxLCqiaA2mw3qLipywbOCmrXVCejoYvlVJkVhFrdGTA/fwSMIkUTZmGz40e0pMB6ODdyFzbZqtVCl/XoiPYFwNMQcHEr0XJ7x3gMu2xouocx4Bie7Sau3zZsZOI8Ej7NumStEjLv42eipcaAxEyiRvB+jqWBp6SJ2UUT2Gd89sbIZzsDsNZSTijCS3ZL14vEZbj5qtWfct2yM0hn2vTdp4SGsF5R9GB03w8fScsvb+21/9Bp2LjVLREDUdWiuXZMaR4rlz0APMrAGf07pVdSJeNY78DlJCM6TPcnunpKTdE2ui/6a72hf7bTg2WMreXbFirT/ jose@irigon-tu" >> /root/.ssh/authorized_keys
RUN git clone https://github.com/irigon/the-one.git /root/the-one-transit

# SSH login fix. Otherwise user is kicked off after login
RUN sed 's@session\s*required\s*pam_loginuid.so@session optional pam_loginuid.so@g' -i /etc/pam.d/sshd

ENV NOTVISIBLE "in users profile"
RUN echo "export VISIBLE=now" >> /etc/profile

EXPOSE 22
CMD ["/usr/sbin/sshd", "-D"]
