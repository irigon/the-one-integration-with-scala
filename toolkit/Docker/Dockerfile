FROM ubuntu:18.04

RUN apt-get update && apt-get install -y openssh-server \
    openjdk-11-jre-headless \
    default-jdk \
    tmux \
    vim \
    git
RUN mkdir /var/run/sshd
RUN mkdir /root/.ssh
RUN echo "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQCyd4DY0hlBTdJFi2kWxLCqiaA2mw3qLipywbOCmrXVCejoYvlVJkVhFrdGTA/fwSMIkUTZmGz40e0pMB6ODdyFzbZqtVCl/XoiPYFwNMQcHEr0XJ7x3gMu2xouocx4Bie7Sau3zZsZOI8Ej7NumStEjLv42eipcaAxEyiRvB+jqWBp6SJ2UUT2Gd89sbIZzsDsNZSTijCS3ZL14vEZbj5qtWfct2yM0hn2vTdp4SGsF5R9GB03w8fScsvb+21/9Bp2LjVLREDUdWiuXZMaR4rlz0APMrAGf07pVdSJeNY78DlJCM6TPcnunpKTdE2ui/6a72hf7bTg2WMreXbFirT/ jose@irigon-tu" >> /root/.ssh/authorized_keys
RUN echo "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAACAQDERBRAZV8OfY6zDvghmD8IneHbWEhLIFhiJHrSgtJJspxDmg7VW0ZIznLKxrb1LFOJJAzLv8a+AG4M2zM6s0Y39wp2xWzhmtfw2zsL/9J8O4ERNr/RhrK9ivuyuory4s7CYMvC7OQEClBYTRSMab2LSxuaADX8rt+KxnUBN34JziO1izD8sR8wNaGMnbvgW+/Qt2x4DBKiaK26SG7oRJ2LM032C5ZK/ATcfIt1SLJOGltDRk7sNp5FNjZvMYz46+S0RNdAaXdxdRWsAvlyEiyGvsZvSBO933ZWEBHWFNNtVGzOB0dUFJmJurXTuFPIRs4EdW1la8j6IZ5b4EKGT09HsMIOkNzP0nSSUeJGFWSFMbQxLW3OZAU+MfpFZeVqCfExVG7khISx+UVIssCGxugoJziOzfb76Q6QibyE6ktxEBLyV+nX0cJYmx8rGkVASk7DS6VEGtT8gDbQjA30jv+baf0Po5ZRwG8ckGnLuk1cyXjnkVonyqXixrfFbR0s/ARlo82Fl27f7fonZ1ypgAeyUissZ7pPaxBREhuZT1GHke1BNa1rUSdwonqNVDToZh4O8/Y0eAQQxJmYaU0NQjScO+CxTl3UOdxEt2QE5kdaLJWyHiETq4BH8UvgTfF3mky2FfP/Cl54EupuG0SLyfD3Yjs0LyccmuqcNAhlWdMbjw== lento@lento-X230" >> /root/.ssh/authorized_keys
RUN git clone https://github.com/irigon/the-one.git /root/the-one-transit

# SSH login fix. Otherwise user is kicked off after login
RUN sed 's@session\s*required\s*pam_loginuid.so@session optional pam_loginuid.so@g' -i /etc/pam.d/sshd

ENV NOTVISIBLE "in users profile"
RUN echo "export VISIBLE=now" >> /etc/profile

EXPOSE 22
CMD ["/usr/sbin/sshd", "-D"]
